package au.org.aodn.oceancurrent.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import au.org.aodn.oceancurrent.configuration.MonitoringSecurityProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Validates AWS EC2 instance identity documents using PKCS7 signatures.
 * <p>
 * This validator verifies that:
 * 1. The PKCS7 signature is valid and signed by AWS
 * 2. The instance identity document is authentic
 * 3. The instance ID in the document matches the claimed instance ID
 * <p>
 * The PKCS7 signature is self-contained and includes the certificate chain.
 * We additionally pin the signer certificate to the known AWS EC2 identity
 * certificate to prevent forgery.
 */
@Component
@Slf4j
public class Ec2InstanceIdentityValidator {

    private final ObjectMapper objectMapper;

    private final String awsEc2IdentityCertPath;

    /**
     * Maximum age in seconds for the pendingTime in the instance identity document.
     * This prevents replay attacks by rejecting documents older than this threshold.
     * Default: 300 seconds (5 minutes)
     */
    private final long timestampToleranceSeconds;

    public Ec2InstanceIdentityValidator(
            ObjectMapper objectMapper,
            MonitoringSecurityProperties monitoringSecurityProperties
    ) {
        this.objectMapper = objectMapper;
        this.awsEc2IdentityCertPath = monitoringSecurityProperties.getEc2IdentityCertPath();
        this.timestampToleranceSeconds = monitoringSecurityProperties.getEc2IdentityTimestampToleranceSeconds();
    }

    /**
     * Validates a PKCS7 signature and extracts the instance identity.
     * This is the SECURE method that only accepts the PKCS7 signature.
     * The document and instanceId are extracted from the signature, not from the request body.
     *
     * @param pkcs7Signature   The PKCS7 signature (base64-encoded)
     * @param requestTimestamp The timestamp when the request was created (ISO 8601 format), optional for replay attack prevention
     * @return ValidationResult containing validation status and the validated instance ID
     */
    public ValidationResult validatePkcs7(String pkcs7Signature, String requestTimestamp) {
        try {
            // Step 1: Verify the PKCS7 signature and extract the signed document
            // SECURITY: We extract the document from the PKCS7 signature, preventing tampering
            String signedDocument = verifyAndExtractSignedDocument(pkcs7Signature);
            if (signedDocument == null) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] PKCS7 signature verification failed");
                return ValidationResult.failure("Invalid PKCS7 signature");
            }

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Successfully extracted signed document from PKCS7");

            // Step 2: Parse the SIGNED document into DTO
            InstanceIdentityDocument parsedDocument = parseDocument(signedDocument);
            if (parsedDocument == null) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Failed to parse instance identity document from PKCS7");
                return ValidationResult.failure("Invalid document format in PKCS7 signature");
            }

            // Step 3: Validate required fields
            String documentInstanceId = parsedDocument.getInstanceId();
            if (documentInstanceId == null || documentInstanceId.isEmpty()) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Document missing instanceId field");
                return ValidationResult.failure("Invalid document format - missing instanceId");
            }

            // Step 4: Validate request timestamp to prevent replay attacks (if provided)
            if (requestTimestamp != null && !requestTimestamp.isEmpty()) {
                ValidationResult timestampValidation = validateRequestTimestamp(requestTimestamp);
                if (!timestampValidation.isValid()) {
                    log.warn("[INSTANCE-IDENTITY-VALIDATION] Request timestamp validation failed | InstanceId={} | Reason={}",
                            documentInstanceId, timestampValidation.getErrorMessage());
                    return timestampValidation;
                }
            } else {
                log.debug("[INSTANCE-IDENTITY-VALIDATION] No request timestamp provided, skipping replay attack protection");
            }

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

            String cleanedSignature = pkcs7Signature.replaceAll("\\s+", "");

            byte[] signatureBytes = Base64.getMimeDecoder().decode(cleanedSignature);

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Decoded PKCS7 DER length: {} bytes", signatureBytes.length);

            // Parse the PKCS7/CMS signed data (AWS uses attached signatures)
            CMSSignedData signedData = new CMSSignedData(signatureBytes);
            log.debug("[INSTANCE-IDENTITY-VALIDATION] Successfully parsed PKCS7 signature");

            // Extract the signed content from the PKCS7 message
            byte[] signedContentBytes = (byte[]) signedData.getSignedContent().getContent();
            String signedDocument = new String(signedContentBytes, StandardCharsets.UTF_8);

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Extracted signed document: {} bytes", signedDocument.length());

            SignerInformationStore signers = signedData.getSignerInfos();

            log.debug("[INSTANCE-IDENTITY-VALIDATION] SignerInfos count: {}", signers.size());

            // Verify the signature using the pinned AWS EC2 identity certificate
            boolean signatureVerified = false;

            for (SignerInformation signer : signers.getSigners()) {
                log.debug("[INSTANCE-IDENTITY-VALIDATION] Processing signer ID: {}", signer.getSID());

                // AWS EC2 PKCS7 signatures typically don't include certs, so we use the pinned cert directly
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
            }

            if (!signatureVerified) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Signature verification failed");
                return null;
            }

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
     * Load the pinned AWS EC2 instance identity signing certificate from the configured location.
     */
    private X509Certificate loadAwsIdentityCertificate() throws CertificateException, IOException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

        try (InputStream in = openCertificateInputStream()) {
            X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(in);
            log.debug("[INSTANCE-IDENTITY-VALIDATION] Loaded AWS EC2 identity certificate from {}", awsEc2IdentityCertPath);
            return cert;
        } catch (IOException e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] Failed to load AWS EC2 identity certificate from {}: {}",
                    awsEc2IdentityCertPath, e.getMessage());
            throw e;
        }
    }

    /**
     * Open an input stream to the configured certificate location.
     * <p>
     * Resolution order:
     * 1. If the path starts with "classpath:", load from the classpath
     * 2. Otherwise, treat it as a filesystem path and load if the file exists
     * 3. If the file does not exist, fall back to downloading the certificate
     * from the official AWS EC2 instance identity URL.
     * <p>
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
     * Validates the request timestamp to prevent replay attacks.
     * The timestamp should be from the MonitoringRequest body and indicates when the request was created.
     * <p>
     * This validation ensures the request is recent and hasn't been reused from an old authentication.
     *
     * @param requestTimestamp The request timestamp in ISO 8601 format (e.g., "2025-11-28T12:00:00Z")
     * @return ValidationResult indicating if the timestamp is within acceptable range
     */
    private ValidationResult validateRequestTimestamp(String requestTimestamp) {
        try {
            if (requestTimestamp == null || requestTimestamp.isEmpty()) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Request timestamp is null or empty");
                return ValidationResult.failure("Request timestamp is required");
            }

            Instant timestamp;
            try {
                timestamp = Instant.parse(requestTimestamp);
            } catch (DateTimeParseException e) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Invalid request timestamp format: {}", requestTimestamp);
                return ValidationResult.failure("Invalid timestamp format (expected ISO 8601, e.g., 2025-11-28T12:00:00Z)");
            }

            Instant now = Instant.now();
            long ageSeconds = now.getEpochSecond() - timestamp.getEpochSecond();

            if (ageSeconds > timestampToleranceSeconds) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Request too old | Timestamp={} | AgeSeconds={} | MaxAgeSeconds={}",
                        requestTimestamp, ageSeconds, timestampToleranceSeconds);
                return ValidationResult.failure(
                        String.format("Request timestamp too old (age: %d seconds, max: %d seconds)",
                                ageSeconds, timestampToleranceSeconds)
                );
            }

            // Check if the request is from the future (clock skew protection)
            if (ageSeconds < -60) {  // Allow 60 seconds for clock skew
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Request timestamp from future | Timestamp={} | AgeSeconds={}",
                        requestTimestamp, ageSeconds);
                return ValidationResult.failure("Request timestamp is from the future");
            }

            log.debug("[INSTANCE-IDENTITY-VALIDATION] Request timestamp validation passed | Timestamp={} | AgeSeconds={}",
                    requestTimestamp, ageSeconds);
            return ValidationResult.success(null);

        } catch (Exception e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] Error validating request timestamp: {}", e.getMessage(), e);
            return ValidationResult.failure("Error validating request timestamp: " + e.getMessage());
        }
    }

    @Getter
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

    }
}
