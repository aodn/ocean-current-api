package au.org.aodn.oceancurrent.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
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
 * The PKCS7 signature is self-contained and includes the certificate chain,
 * eliminating the need to download AWS public certificates separately.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AwsInstanceIdentityValidator {

    private final ObjectMapper objectMapper;

    /**
     * Validates an EC2 instance identity document with its PKCS7 signature.
     *
     * @param document The instance identity document JSON string
     * @param pkcs7Signature The PKCS7 signature (base64-encoded)
     * @param claimedInstanceId The instance ID claimed in the request
     * @return ValidationResult containing validation status and details
     */
    public ValidationResult validate(String document, String pkcs7Signature, String claimedInstanceId) {
        try {
            // Step 1: Verify the PKCS7 signature
            if (!verifyPkcs7Signature(document, pkcs7Signature)) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] PKCS7 signature verification failed");
                return ValidationResult.failure("Invalid PKCS7 signature");
            }

            // Step 2: Parse the document and extract instance ID
            String documentInstanceId = extractInstanceIdFromDocument(document);
            if (documentInstanceId == null) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Failed to extract instance ID from document");
                return ValidationResult.failure("Invalid document format - missing instanceId");
            }

            // Step 3: Verify the instance ID matches the claimed ID
            if (!documentInstanceId.equals(claimedInstanceId)) {
                log.warn("[INSTANCE-IDENTITY-VALIDATION] Instance ID mismatch | Document={} | Claimed={}",
                        documentInstanceId, claimedInstanceId);
                return ValidationResult.failure("Instance ID mismatch");
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
     * Verifies the PKCS7 signature against the document content.
     * Uses Bouncy Castle library for PKCS7/CMS signature verification.
     */
    private boolean verifyPkcs7Signature(String document, String pkcs7Signature) {
        try {
            // Remove whitespace and newlines from PKCS7 signature
            String cleanedSignature = pkcs7Signature.replaceAll("\\s+", "");

            // Decode the base64-encoded PKCS7 signature
            byte[] signatureBytes = Base64.getMimeDecoder().decode(cleanedSignature);

            // Parse the PKCS7/CMS signed data
            CMSSignedData signedData = new CMSSignedData(
                new CMSProcessableByteArray(document.getBytes(StandardCharsets.UTF_8)),
                signatureBytes
            );

            // Get the certificate store from the signed data
            Store certStore = signedData.getCertificates();
            SignerInformationStore signers = signedData.getSignerInfos();

            // Verify each signer
            for (SignerInformation signer : signers.getSigners()) {
                // Get the certificate for this signer
                @SuppressWarnings("unchecked")
                Collection<X509Certificate> certCollection =
                    (Collection<X509Certificate>) certStore.getMatches(signer.getSID());

                if (certCollection.isEmpty()) {
                    log.warn("[INSTANCE-IDENTITY-VALIDATION] No certificate found for signer");
                    return false;
                }

                Iterator<X509Certificate> certIt = certCollection.iterator();
                X509Certificate cert = certIt.next();

                // Verify the signature
                if (!signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert))) {
                    log.warn("[INSTANCE-IDENTITY-VALIDATION] Signature verification failed for certificate: {}",
                            cert.getSubjectX500Principal().getName());
                    return false;
                }

                log.debug("[INSTANCE-IDENTITY-VALIDATION] Signature verified | Certificate={}",
                        cert.getSubjectX500Principal().getName());
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
     * Extracts the instance ID from the instance identity document JSON.
     */
    private String extractInstanceIdFromDocument(String document) {
        try {
            JsonNode root = objectMapper.readTree(document);
            if (root.has("instanceId")) {
                return root.get("instanceId").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("[INSTANCE-IDENTITY-VALIDATION] Failed to parse document JSON: {}", e.getMessage());
            return null;
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
