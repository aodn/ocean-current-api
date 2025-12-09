package au.org.aodn.oceancurrent.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;

/**
 * Validates AWS EC2 instance identity documents using PKCS7 signatures.
 *
 * This validator verifies that:
 * 1. The PKCS7 signature is valid and signed by AWS
 * 2. The instance identity document is authentic
 * 3. The instance ID in the document matches the claimed instance ID
 *
 * The PKCS7 signature is self-contained and includes the certificate chain.
 * We additionally pin the signer certificate to the known AWS EC2 identity
 * certificate to prevent forgery.
 */
@Component
@Slf4j
public class AwsInstanceIdentityValidator {

    private final ObjectMapper objectMapper;

    /**
     * Path to the AWS EC2 instance identity signing certificate.
     *
     * Supports either:
     * - A filesystem path (e.g. /app/ec2-instance-identity.pem)
     * - A classpath resource with prefix "classpath:" (e.g. classpath:aws/ec2-instance-identity.pem)
     *
     * Default is the path used in the Docker image.
     */
    private final String awsEc2IdentityCertPath;

    /**
     * Maximum age in seconds for the pendingTime in the instance identity document.
     * This prevents replay attacks by rejecting documents older than this threshold.
     * Default: 300 seconds (5 minutes)
     */
    private final long timestampToleranceSeconds;

    public AwsInstanceIdentityValidator(
            ObjectMapper objectMapper,
            @Value("${app.ec2-identity-cert-path:classpath:aws/ec2-instance-identity.pem}")
            String awsEc2IdentityCertPath,
            @Value("${app.ec2-identity-timestamp-tolerance-seconds:300}")
            long timestampToleranceSeconds
    ) {
        this.objectMapper = objectMapper;
        this.awsEc2IdentityCertPath = awsEc2IdentityCertPath;
        this.timestampToleranceSeconds = timestampToleranceSeconds;
    }

    /**
     * Validates an EC2 instance identity document with its PKCS7 signature.
     *
     * @param document The instance identity document JSON string (NOT TRUSTED - for reference only)
     * @param pkcs7Signature The PKCS7 signature (base64-encoded)
     * @param claimedInstanceId The instance ID claimed in the request
     * @return ValidationResult containing validation status and details
     */
    public ValidationResult validate(String document, String pkcs7Signature, String claimedInstanceId) {
        try {
            // Step 1: Verify the PKCS7 signature and extract the signed document
            // SECURITY: We extract the document from the PKCS7 signature, not from the request body
            // This prevents tampering with the document content
            String signedDocument = verifyAndExtractSignedDocument(pkcs7Signature);
            if (signedDocument == null) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] PKCS7 signature verification failed");
                return ValidationResult.failure("Invalid PKCS7 signature");
            }

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Successfully extracted signed document from PKCS7");

            // Step 2: Parse the SIGNED document into DTO (NOT the document from request body)
            InstanceIdentityDocument parsedDocument = parseDocument(signedDocument);
            if (parsedDocument == null) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Failed to parse instance identity document from PKCS7");
                return ValidationResult.failure("Invalid document format in PKCS7 signature");
            }

            // Step 3: Validate required fields
            String documentInstanceId = parsedDocument.getInstanceId();
            log.info("###################{}", documentInstanceId);
            if (documentInstanceId == null || documentInstanceId.isEmpty()) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Document missing instanceId field");
                return ValidationResult.failure("Invalid document format - missing instanceId");
            }

            // Step 4: Verify the instance ID matches the claimed ID
            if (!documentInstanceId.equals(claimedInstanceId)) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Instance ID mismatch | Document={} | Claimed={}",
                        documentInstanceId, claimedInstanceId);
                return ValidationResult.failure("Instance ID mismatch");
            }

            // Step 5: Validate pendingTime to prevent replay attacks (using parsed DTO)
//            ValidationResult timestampValidation = validateDocumentTimestamp(parsedDocument);
//            if (!timestampValidation.isValid()) {
//                log.warn("[INSTANCE-IDENTITY-VALIDATION] Timestamp validation failed | InstanceId={} | Reason={}",
//                        documentInstanceId, timestampValidation.getErrorMessage());
//                return timestampValidation;
//            }

            log.info("[INSTANCE-IDENTITY-VALIDATION] Successfully validated instance identity | InstanceId={}",
                    documentInstanceId);
            return ValidationResult.success(documentInstanceId);

        } catch (Exception e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] Validation error: {}", e.getMessage(), e);
            return ValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * Verifies the PKCS7 signature and extracts the signed document content.
     * This is the SECURE way to validate - we extract the document from the PKCS7
     * instead of trusting the document passed in the request body.
     *
     * @param pkcs7Signature The PKCS7 signature (base64-encoded)
     * @return The signed document content, or null if verification fails
     */
    private String verifyAndExtractSignedDocument(String pkcs7Signature) {
        try {
            X509Certificate awsIdentityCert = loadAwsIdentityCertificate();

            // Remove whitespace and newlines from PKCS7 signature
            String cleanedSignature = pkcs7Signature.replaceAll("\\s+", "");

            log.debug("[INSTANCE-IDENTITY-VALIDATION] PKCS7 signature length (cleaned): {} characters", cleanedSignature.length());
            log.debug("[INSTANCE-IDENTITY-VALIDATION] PKCS7 signature (first 100 chars): {}",
                cleanedSignature.substring(0, Math.min(100, cleanedSignature.length())));

            // Decode the base64-encoded PKCS7 signature
            byte[] signatureBytes = Base64.getMimeDecoder().decode(cleanedSignature);

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Decoded PKCS7 DER length: {} bytes", signatureBytes.length);

            // Parse the PKCS7/CMS signed data (AWS uses attached signatures)
            CMSSignedData signedData = new CMSSignedData(signatureBytes);
            log.debug("[INSTANCE-IDENTITY-VALIDATION] Successfully parsed PKCS7 signature");

            // Extract the signed content from the PKCS7 message
            byte[] signedContentBytes = (byte[]) signedData.getSignedContent().getContent();
            String signedDocument = new String(signedContentBytes, StandardCharsets.UTF_8);

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Extracted signed document: {} bytes", signedDocument.length());

            // Get the certificate store and signers
            Store certStore = signedData.getCertificates();
            SignerInformationStore signers = signedData.getSignerInfos();

            log.debug("[INSTANCE-IDENTITY-VALIDATION] SignerInfos count: {}", signers.size());

            // Get all certificates from the PKCS7 message
            @SuppressWarnings("unchecked")
            Collection<X509Certificate> allCerts = (Collection<X509Certificate>) certStore.getMatches(null);

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Total certificates in PKCS7: {}", allCerts.size());

            // AWS EC2 PKCS7 signatures typically do NOT include the certificate chain
            // We need to verify using our pinned certificate directly
            if (allCerts.isEmpty()) {
                log.debug("[INSTANCE-IDENTITY-VALIDATION] No certificates embedded in PKCS7, will use pinned AWS cert for verification");
            } else {
                // Log all available certificates for debugging
                for (X509Certificate cert : allCerts) {
                    log.debug("[INSTANCE-IDENTITY-VALIDATION] Available cert in PKCS7: {}", cert.getSubjectX500Principal().getName());
                }
            }

            // Verify the signature using the pinned AWS EC2 identity certificate
            boolean signatureVerified = false;

            for (SignerInformation signer : signers.getSigners()) {
                log.debug("[INSTANCE-IDENTITY-VALIDATION] Processing signer ID: {}", signer.getSID());

                // AWS EC2 PKCS7 signatures typically don't include certs, so we use the pinned cert directly
                if (allCerts.isEmpty()) {
                    log.debug("[INSTANCE-IDENTITY-VALIDATION] Verifying signature using pinned AWS certificate: {}",
                        awsIdentityCert.getSubjectX500Principal().getName());

                    // Verify the signature with the pinned AWS certificate
                    try {
                        if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(awsIdentityCert))) {
                            log.info("[INSTANCE-IDENTITY-VALIDATION] Signature verified with pinned AWS certificate | Certificate={}",
                                    awsIdentityCert.getSubjectX500Principal().getName());
                            signatureVerified = true;
                            break;
                        } else {
                            log.warn("[INSTANCE-IDENTITY-VALIDATION] Signature verification failed for pinned AWS certificate");
                        }
                    } catch (Exception e) {
                        log.warn("[INSTANCE-IDENTITY-VALIDATION] Exception during signature verification: {}", e.getMessage(), e);
                    }
                } else {
                    // If certificates are embedded, try to find one that matches our pinned cert
                    @SuppressWarnings("unchecked")
                    Collection<X509Certificate> certCollection =
                        (Collection<X509Certificate>) certStore.getMatches(signer.getSID());

                    // If no certificate found by ID, try all certificates
                    if (certCollection.isEmpty()) {
                        log.debug("[INSTANCE-IDENTITY-VALIDATION] No certificate found by signer ID, trying all certificates");
                        certCollection = allCerts;
                    }

                    // Try each certificate to find one that matches our pinned cert and verifies the signature
                    for (X509Certificate cert : certCollection) {
                        log.debug("[INSTANCE-IDENTITY-VALIDATION] Trying certificate: {}", cert.getSubjectX500Principal().getName());

                        // First check if this certificate matches the pinned AWS EC2 identity certificate
                        if (!certificateMatchesPinnedAwsCert(cert, awsIdentityCert)) {
                            log.debug("[INSTANCE-IDENTITY-VALIDATION] Certificate does not match pinned AWS cert, skipping");
                            continue;
                        }

                        log.debug("[INSTANCE-IDENTITY-VALIDATION] Certificate matches pinned AWS cert, verifying signature");

                        // Verify the signature with this certificate
                        try {
                            if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert))) {
                                log.info("[INSTANCE-IDENTITY-VALIDATION] Signature verified with pinned AWS certificate | Certificate={}",
                                        cert.getSubjectX500Principal().getName());
                                signatureVerified = true;
                                break;
                            } else {
                                log.debug("[INSTANCE-IDENTITY-VALIDATION] Signature verification failed for certificate: {}",
                                        cert.getSubjectX500Principal().getName());
                            }
                        } catch (Exception e) {
                            log.debug("[INSTANCE-IDENTITY-VALIDATION] Exception during signature verification: {}", e.getMessage());
                        }
                    }
                }

                if (signatureVerified) {
                    break;
                }
            }

            if (!signatureVerified) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Signature verification failed");
                return null;
            }

            // Return the SIGNED document content (not the one from request body)
            return signedDocument;

        } catch (CMSException e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] CMS signature verification error: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] PKCS7 verification error: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * @deprecated This method is replaced by verifyAndExtractSignedDocument for security.
     * Keeping for reference but should not be used.
     */
    @Deprecated
    private boolean verifyPkcs7Signature(String document, String pkcs7Signature) {
        try {
            X509Certificate awsIdentityCert = loadAwsIdentityCertificate();

            // Remove whitespace and newlines from PKCS7 signature
            String cleanedSignature = pkcs7Signature.replaceAll("\\s+", "");

            log.debug("[INSTANCE-IDENTITY-VALIDATION] PKCS7 signature length (cleaned): {} characters", cleanedSignature.length());
            log.debug("[INSTANCE-IDENTITY-VALIDATION] PKCS7 signature (first 100 chars): {}",
                cleanedSignature.substring(0, Math.min(100, cleanedSignature.length())));
            log.debug("[INSTANCE-IDENTITY-VALIDATION] Document length: {} bytes", document.length());

            // Decode the base64-encoded PKCS7 signature
            byte[] signatureBytes = Base64.getMimeDecoder().decode(cleanedSignature);

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Decoded PKCS7 DER length: {} bytes", signatureBytes.length);

            // Parse the PKCS7/CMS signed data
            // AWS PKCS7 signatures can be either attached (content included) or detached (content separate)
            // Try parsing as attached signature first (no content parameter)
            CMSSignedData signedData;
            try {
                // First try: Parse without providing content (attached signature)
                signedData = new CMSSignedData(signatureBytes);
                log.debug("[INSTANCE-IDENTITY-VALIDATION] Successfully parsed as attached PKCS7 signature");
            } catch (CMSException e) {
                // Second try: Parse with content (detached signature)
                log.debug("[INSTANCE-IDENTITY-VALIDATION] Failed to parse as attached signature, trying detached: {}", e.getMessage());
                signedData = new CMSSignedData(
                    new CMSProcessableByteArray(document.getBytes(StandardCharsets.UTF_8)),
                    signatureBytes
                );
                log.debug("[INSTANCE-IDENTITY-VALIDATION] Successfully parsed as detached PKCS7 signature");
            }

            // Get the certificate store from the signed data
            Store certStore = signedData.getCertificates();
            SignerInformationStore signers = signedData.getSignerInfos();

            log.debug("[INSTANCE-IDENTITY-VALIDATION] SignerInfos count: {}", signers.size());

            // Get all certificates from the PKCS7 message
            @SuppressWarnings("unchecked")
            Collection<X509Certificate> allCerts = (Collection<X509Certificate>) certStore.getMatches(null);

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Total certificates in PKCS7: {}", allCerts.size());

            // AWS EC2 PKCS7 signatures typically do NOT include the certificate chain
            // We need to verify using our pinned certificate directly
            if (allCerts.isEmpty()) {
                log.debug("[INSTANCE-IDENTITY-VALIDATION] No certificates embedded in PKCS7, will use pinned AWS cert for verification");
            } else {
                // Log all available certificates for debugging
                for (X509Certificate cert : allCerts) {
                    log.debug("[INSTANCE-IDENTITY-VALIDATION] Available cert in PKCS7: {}", cert.getSubjectX500Principal().getName());
                }
            }

            // Verify each signer using the pinned AWS EC2 identity certificate
            boolean awsSignerVerified = false;

            for (SignerInformation signer : signers.getSigners()) {
                log.debug("[INSTANCE-IDENTITY-VALIDATION] Processing signer ID: {}", signer.getSID());

                // AWS EC2 PKCS7 signatures typically don't include certs, so we use the pinned cert directly
                if (allCerts.isEmpty()) {
                    log.debug("[INSTANCE-IDENTITY-VALIDATION] Verifying signature using pinned AWS certificate: {}",
                        awsIdentityCert.getSubjectX500Principal().getName());

                    // Verify the signature with the pinned AWS certificate
                    try {
                        if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(awsIdentityCert))) {
                            log.info("[INSTANCE-IDENTITY-VALIDATION] Signature verified with pinned AWS certificate | Certificate={}",
                                    awsIdentityCert.getSubjectX500Principal().getName());
                            awsSignerVerified = true;
                            break; // Found valid signature, exit loop
                        } else {
                            log.warn("[INSTANCE-IDENTITY-VALIDATION] Signature verification failed for pinned AWS certificate");
                        }
                    } catch (Exception e) {
                        log.warn("[INSTANCE-IDENTITY-VALIDATION] Exception during signature verification: {}", e.getMessage(), e);
                    }
                } else {
                    // If certificates are embedded, try to find one that matches our pinned cert
                    @SuppressWarnings("unchecked")
                    Collection<X509Certificate> certCollection =
                        (Collection<X509Certificate>) certStore.getMatches(signer.getSID());

                    // If no certificate found by ID, try all certificates
                    if (certCollection.isEmpty()) {
                        log.debug("[INSTANCE-IDENTITY-VALIDATION] No certificate found by signer ID, trying all certificates");
                        certCollection = allCerts;
                    }

                    // Try each certificate to find one that matches our pinned cert and verifies the signature
                    for (X509Certificate cert : certCollection) {
                        log.debug("[INSTANCE-IDENTITY-VALIDATION] Trying certificate: {}", cert.getSubjectX500Principal().getName());

                        // First check if this certificate matches the pinned AWS EC2 identity certificate
                        if (!certificateMatchesPinnedAwsCert(cert, awsIdentityCert)) {
                            log.debug("[INSTANCE-IDENTITY-VALIDATION] Certificate does not match pinned AWS cert, skipping");
                            continue;
                        }

                        log.debug("[INSTANCE-IDENTITY-VALIDATION] Certificate matches pinned AWS cert, verifying signature");

                        // Verify the signature with this certificate
                        try {
                            if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert))) {
                                log.info("[INSTANCE-IDENTITY-VALIDATION] Signature verified with pinned AWS certificate | Certificate={}",
                                        cert.getSubjectX500Principal().getName());
                                awsSignerVerified = true;
                                break; // Found valid signature, exit inner loop
                            } else {
                                log.debug("[INSTANCE-IDENTITY-VALIDATION] Signature verification failed for certificate: {}",
                                        cert.getSubjectX500Principal().getName());
                            }
                        } catch (Exception e) {
                            log.debug("[INSTANCE-IDENTITY-VALIDATION] Exception during signature verification: {}", e.getMessage());
                        }
                    }
                }

                if (awsSignerVerified) {
                    break; // Exit outer loop if we found a valid signature
                }
            }

            if (!awsSignerVerified) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] No signer matched the pinned AWS EC2 identity certificate");
                return false;
            }

            return true;

        } catch (CMSException e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] CMS signature verification error: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] PKCS7 verification error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Load the pinned AWS EC2 instance identity signing certificate from the configured location.
     */
    private X509Certificate loadAwsIdentityCertificate() throws CertificateException, IOException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

        try (InputStream in = openCertificateInputStream()) {
            X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(in);
            log.debug("[INSTANCE-IDENTITY-VALIDATION] Loaded AWS EC2 identity certificate from {}", awsEc2IdentityCertPath);
            log.info("#####: {}", cert);
            return cert;
        } catch (IOException e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] Failed to load AWS EC2 identity certificate from {}: {}",
                    awsEc2IdentityCertPath, e.getMessage());
            throw e;
        }
    }

    /**
     * Open an input stream to the configured certificate location.
     *
     * Resolution order:
     * 1. If the path starts with "classpath:", load from the classpath
     * 2. Otherwise, treat it as a filesystem path and load if the file exists
     * 3. If the file does not exist, fall back to downloading the certificate
     *    from the official AWS EC2 instance identity URL.
     *
     * In production, the Docker image provides the certificate at a fixed
     * filesystem path, so the download fallback is primarily for local
     * development convenience.
     */
    private InputStream openCertificateInputStream() throws IOException {
        if (awsEc2IdentityCertPath.startsWith("classpath:")) {
            String resourcePath = awsEc2IdentityCertPath.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                throw new IOException("Classpath certificate resource not found: " + awsEc2IdentityCertPath);
            }
            return resource.getInputStream();
        }

        File file = new File(awsEc2IdentityCertPath);
        if (file.exists()) {
            return new FileInputStream(file);
        }

        // No valid certificate source found – fail fast with a clear error.
        throw new IOException("EC2 identity certificate not found at path: " + awsEc2IdentityCertPath +
                ". Provide the certificate as a classpath resource (e.g. classpath:aws/ec2-instance-identity.pem) " +
                "or configure 'app.ec2-identity-cert-path' to a valid filesystem path.");
    }

    /**
     * Compare the signer certificate with the pinned AWS EC2 identity certificate using SHA-256 fingerprint.
     */
    private boolean certificateMatchesPinnedAwsCert(X509Certificate signerCert, X509Certificate awsCert) {
        try {
            String signerFingerprint = getCertificateSha256Fingerprint(signerCert);
            String awsFingerprint = getCertificateSha256Fingerprint(awsCert);

            boolean matches = signerFingerprint.equalsIgnoreCase(awsFingerprint);

            if (!matches) {
                log.debug("[INSTANCE-IDENTITY-VALIDATION] Certificate fingerprint mismatch | Signer={} | AWS={}",
                        signerFingerprint, awsFingerprint);
            }

            return matches;
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] Failed to compute certificate fingerprint: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Compute SHA-256 fingerprint of a certificate.
     */
    private String getCertificateSha256Fingerprint(X509Certificate certificate)
            throws CertificateEncodingException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] der = certificate.getEncoded();
        byte[] digest = md.digest(der);

        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Parses the instance identity document JSON string into a structured DTO.
     *
     * @param document The instance identity document JSON string
     * @return InstanceIdentityDocument object, or null if parsing fails
     */
    private InstanceIdentityDocument parseDocument(String document) {
        try {
            return objectMapper.readValue(document, InstanceIdentityDocument.class);
        } catch (Exception e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] Failed to parse instance identity document: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validates the pendingTime field in the instance identity document to prevent replay attacks.
     * The pendingTime represents when the instance was launched and is part of the signed document.
     *
     * This validation ensures the document is recent and hasn't been reused from an old authentication.
     *
     * @param document The parsed instance identity document
     * @return ValidationResult indicating if the timestamp is within acceptable range
     */
    private ValidationResult validateDocumentTimestamp(InstanceIdentityDocument document) {
        try {
            String pendingTimeStr = document.getPendingTime();

            if (pendingTimeStr == null || pendingTimeStr.isEmpty()) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Document missing pendingTime field");
                return ValidationResult.failure("Document missing pendingTime field");
            }

            Instant pendingTime;
            try {
                pendingTime = Instant.parse(pendingTimeStr);
            } catch (DateTimeParseException e) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Invalid pendingTime format: {}", pendingTimeStr);
                return ValidationResult.failure("Invalid pendingTime format");
            }

            Instant now = Instant.now();
            long ageSeconds = now.getEpochSecond() - pendingTime.getEpochSecond();

            // Check if the document is too old
            if (ageSeconds > timestampToleranceSeconds) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Document too old | PendingTime={} | AgeSeconds={} | MaxAgeSeconds={}",
                        pendingTimeStr, ageSeconds, timestampToleranceSeconds);
                return ValidationResult.failure(
                    String.format("Document timestamp too old (age: %d seconds, max: %d seconds)",
                        ageSeconds, timestampToleranceSeconds)
                );
            }

            // Check if the document is from the future (clock skew protection)
            if (ageSeconds < -60) {  // Allow 60 seconds for clock skew
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Document from future | PendingTime={} | AgeSeconds={}",
                        pendingTimeStr, ageSeconds);
                return ValidationResult.failure("Document timestamp is from the future");
            }

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Timestamp validation passed | PendingTime={} | AgeSeconds={}",
                    pendingTimeStr, ageSeconds);
            return ValidationResult.success(null);

        } catch (Exception e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] Error validating timestamp: {}", e.getMessage(), e);
            return ValidationResult.failure("Error validating timestamp: " + e.getMessage());
        }
    }

    /**
     * Result of instance identity validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String instanceId;
        private final String errorMessage;

        private ValidationResult(boolean valid, String instanceId, String errorMessage) {
            this.valid = valid;
            this.instanceId = instanceId;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success(String instanceId) {
            return new ValidationResult(true, instanceId, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, null, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
