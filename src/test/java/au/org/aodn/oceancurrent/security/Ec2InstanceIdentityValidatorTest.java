package au.org.aodn.oceancurrent.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for Ec2InstanceIdentityValidator.
 * Tests PKCS7 signature verification and timestamp validation logic.
 * <p>
 * Note: Full PKCS7 signature verification requires real AWS signatures or extensive mocking.
 * These tests focus on input validation and error handling paths.
 */
@SpringBootTest
public class Ec2InstanceIdentityValidatorTest {

    @Autowired
    private Ec2InstanceIdentityValidator validator;

    @Test
    public void testValidatePkcs7_WithNullPkcs7_ReturnFailure() {
        // Given: null PKCS7 signature
        String pkcs7 = null;

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, null);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertNull(result.getInstanceId());
    }

    @Test
    public void testValidatePkcs7_WithEmptyPkcs7_ReturnFailure() {
        // Given: Empty PKCS7 signature
        String pkcs7 = "";

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, null);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertNull(result.getInstanceId());
    }

    @Test
    public void testValidatePkcs7_WithInvalidPkcs7Format_ReturnFailure() {
        // Given: Invalid PKCS7 signature format
        String pkcs7 = "not-a-valid-pkcs7-signature";

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, null);

        // Then: Validation should fail with appropriate error message
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertNull(result.getInstanceId());
    }

    @Test
    public void testValidatePkcs7_WithInvalidBase64_ReturnFailure() {
        // Given: Invalid base64 in PKCS7 signature
        String pkcs7 = "!!!invalid-base64!!!";

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, null);

        // Then: Validation should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertNull(result.getInstanceId());
    }

    @Test
    public void testValidatePkcs7_WithValidTimestamp_NoTimestampError() {
        // Given: Invalid PKCS7 but valid recent timestamp
        String pkcs7 = "invalid-signature";
        String recentTimestamp = Instant.now().toString();

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, recentTimestamp);

        // Then: Should fail on PKCS7 validation, not timestamp
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        // Error should be about PKCS7, not timestamp
        assertFalse(result.getErrorMessage().contains("timestamp"));
    }

    @Test
    public void testValidatePkcs7_WithOldTimestamp_WouldFailIfPkcs7Valid() {
        // Given: Invalid PKCS7 with old timestamp (6 minutes ago, beyond 5-minute tolerance)
        String pkcs7 = "invalid-signature";
        String oldTimestamp = Instant.now().minus(6, ChronoUnit.MINUTES).toString();

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, oldTimestamp);

        // Then: Should fail (on PKCS7 validation first, but would fail on timestamp too)
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testValidatePkcs7_WithFutureTimestamp_WouldFailIfPkcs7Valid() {
        // Given: Invalid PKCS7 with future timestamp (2 minutes in future, beyond 1-minute skew tolerance)
        String pkcs7 = "invalid-signature";
        String futureTimestamp = Instant.now().plus(2, ChronoUnit.MINUTES).toString();

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, futureTimestamp);

        // Then: Should fail
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testValidatePkcs7_WithInvalidTimestampFormat_NoTimestampError() {
        // Given: Invalid PKCS7 with invalid timestamp format
        String pkcs7 = "invalid-signature";
        String invalidTimestamp = "not-a-valid-iso8601-timestamp";

        // When: Validate is called
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, invalidTimestamp);

        // Then: Should fail (on PKCS7 first)
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testValidatePkcs7_WithoutTimestamp_SkipsReplayProtection() {
        // Given: Invalid PKCS7 without timestamp
        String pkcs7 = "invalid-signature";

        // When: Validate is called without timestamp
        Ec2InstanceIdentityValidator.ValidationResult result =
            validator.validatePkcs7(pkcs7, null);

        // Then: Should fail on PKCS7 validation, not timestamp
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        // Should NOT complain about missing timestamp
        assertFalse(result.getErrorMessage().toLowerCase().contains("timestamp required"));
    }

    // ==================== ValidationResult Tests ====================

    @Test
    public void testValidationResult_Success() {
        // Given: A successful validation result with instance ID
        String instanceId = "i-0123456789abcdef0";

        // When: Creating a success result
        Ec2InstanceIdentityValidator.ValidationResult result =
            Ec2InstanceIdentityValidator.ValidationResult.success(instanceId);

        // Then: Result should be valid with instance ID
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

        // Then: Result should be invalid without instance ID
        assertFalse(result.isValid());
        assertNull(result.getInstanceId());
        assertEquals(errorMessage, result.getErrorMessage());
    }
}
