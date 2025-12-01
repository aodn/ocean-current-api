package au.org.aodn.oceancurrent.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for AwsInstanceIdentityValidator.
 * Tests PKCS7 signature verification and instance ID validation logic.
 */
@SpringBootTest
public class AwsInstanceIdentityValidatorTest {

    @Autowired
    private AwsInstanceIdentityValidator validator;

    private static final String MOCK_INSTANCE_ID = "i-0123456789abcdef0";
    private static final String MOCK_DOCUMENT = "{\"instanceId\":\"i-0123456789abcdef0\",\"region\":\"ap-southeast-2\",\"accountId\":\"123456789012\"}";
    private static final String MOCK_PKCS7 = "MIAGCSqGSIb3DQEHAqCAMIACAQExCzAJBgUrDgMCGgUAMIAGCSqGSIb3DQEHAaCAJIAEggHc";

    @BeforeEach
    public void setUp() {
        // validator is autowired
    }

    @Test
    public void testValidate_WithMissingDocument_ReturnFailure() {
        // Given: null document
        String document = null;

        // When: Validate is called
        AwsInstanceIdentityValidator.ValidationResult result =
            validator.validate(document, MOCK_PKCS7, MOCK_INSTANCE_ID);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertNull(result.getInstanceId());
    }

    @Test
    public void testValidate_WithMissingPkcs7_ReturnFailure() {
        // Given: null PKCS7 signature
        String pkcs7 = null;

        // When: Validate is called
        AwsInstanceIdentityValidator.ValidationResult result =
            validator.validate(MOCK_DOCUMENT, pkcs7, MOCK_INSTANCE_ID);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testValidate_WithInvalidPkcs7Format_ReturnFailure() {
        // Given: Invalid PKCS7 signature format
        String pkcs7 = "not-a-valid-pkcs7-signature";

        // When: Validate is called
        AwsInstanceIdentityValidator.ValidationResult result =
            validator.validate(MOCK_DOCUMENT, pkcs7, MOCK_INSTANCE_ID);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Invalid PKCS7 signature"));
    }

    @Test
    public void testValidate_WithMalformedDocument_ReturnFailure() {
        // Given: Malformed JSON document
        String document = "{invalid-json";

        // When: Validate is called
        AwsInstanceIdentityValidator.ValidationResult result =
            validator.validate(document, MOCK_PKCS7, MOCK_INSTANCE_ID);

        // Then: Validation should fail (either PKCS7 verification or JSON parsing will fail)
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testValidate_WithMissingInstanceIdInDocument_ReturnFailure() {
        // Given: Document without instanceId field
        String document = "{\"region\":\"ap-southeast-2\",\"accountId\":\"123456789012\"}";

        // When: Validate is called
        AwsInstanceIdentityValidator.ValidationResult result =
            validator.validate(document, MOCK_PKCS7, MOCK_INSTANCE_ID);

        // Then: Validation should fail (PKCS7 verification will fail first, but if it passed, missing instanceId should be caught)
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testValidate_WithMismatchedInstanceId_ReturnFailure() {
        // Given: Document with different instance ID than claimed
        String document = "{\"instanceId\":\"i-different-instance\",\"region\":\"ap-southeast-2\"}";

        // When: Validate is called (signature validation will fail, but if it passed, instance ID mismatch should catch it)
        AwsInstanceIdentityValidator.ValidationResult result =
            validator.validate(document, MOCK_PKCS7, MOCK_INSTANCE_ID);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testValidationResult_Success() {
        // Given: A successful validation result
        String instanceId = "i-0123456789abcdef0";

        // When: Creating a success result
        AwsInstanceIdentityValidator.ValidationResult result =
            AwsInstanceIdentityValidator.ValidationResult.success(instanceId);

        // Then: Result should be valid
        assertTrue(result.isValid());
        assertEquals(instanceId, result.getInstanceId());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void testValidationResult_Failure() {
        // Given: A failure message
        String errorMessage = "Signature verification failed";

        // When: Creating a failure result
        AwsInstanceIdentityValidator.ValidationResult result =
            AwsInstanceIdentityValidator.ValidationResult.failure(errorMessage);

        // Then: Result should be invalid
        assertFalse(result.isValid());
        assertNull(result.getInstanceId());
        assertEquals(errorMessage, result.getErrorMessage());
    }
}
