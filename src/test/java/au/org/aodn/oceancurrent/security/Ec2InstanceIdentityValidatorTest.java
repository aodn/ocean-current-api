package au.org.aodn.oceancurrent.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for Ec2InstanceIdentityValidator.
 * Tests PKCS7 signature verification and instance ID validation logic.
 */
@SpringBootTest
public class Ec2InstanceIdentityValidatorTest {

    @Autowired
    private Ec2InstanceIdentityValidator validator;

    private static final String MOCK_INSTANCE_ID = "i-0123456789abcdef0";
    private static final String MOCK_PKCS7 = "MIAGCSqGSIb3DQEHAqCAMIACAQExCzAJBgUrDgMCGgUAMIAGCSqGSIb3DQEHAaCAJIAEggHc";

    @BeforeEach
    public void setUp() {
        // validator is autowired
    }

    @Test
    public void testValidatePkcs7_WithMissingPkcs7_ReturnFailure() {
        // Given: null PKCS7 signature
        String pkcs7 = null;

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, null);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testValidatePkcs7_WithInvalidPkcs7Format_ReturnFailure() {
        // Given: Invalid PKCS7 signature format
        String pkcs7 = "not-a-valid-pkcs7-signature";

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, null);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Invalid PKCS7 signature") ||
                   result.getErrorMessage().contains("PKCS7"));
    }

    @Test
    public void testValidationResult_Success() {
        // Given: A successful validation result
        String instanceId = "i-0123456789abcdef0";

        // When: Creating a success result
        Ec2InstanceIdentityValidator.ValidationResult result =
            Ec2InstanceIdentityValidator.ValidationResult.success(instanceId);

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
        Ec2InstanceIdentityValidator.ValidationResult result =
            Ec2InstanceIdentityValidator.ValidationResult.failure(errorMessage);

        // Then: Result should be invalid
        assertFalse(result.isValid());
        assertNull(result.getInstanceId());
        assertEquals(errorMessage, result.getErrorMessage());
    }
}
