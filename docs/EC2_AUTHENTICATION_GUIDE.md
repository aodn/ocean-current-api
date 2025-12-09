# AWS EC2 Instance Identity Authentication Guide

## Overview

This document explains how the Ocean Current API authenticates EC2 instances using AWS EC2 Instance Identity PKCS7 signatures. This authentication mechanism provides cryptographic proof that requests genuinely originate from authorized EC2 instances.

**Key Security Feature**: Only the PKCS7 signature is sent in requests. The server extracts the instance identity document and instance ID from the PKCS7 signature during validation, preventing tampering.

## Table of Contents

1. [How It Works](#how-it-works)
2. [Security Features](#security-features)
3. [Architecture](#architecture)
4. [Configuration](#configuration)
5. [Usage](#usage)
6. [Testing](#testing)
7. [Troubleshooting](#troubleshooting)

## How It Works

### The EC2 Instance Identity Document

Every EC2 instance has access to a special metadata service at `http://169.254.169.254`. This service provides:

1. **Instance Identity Document**: A JSON document containing instance metadata (instance ID, region, account ID, etc.)
2. **PKCS7 Signature**: A cryptographic signature of the document, signed by AWS using their private key

### Authentication Flow

```
┌─────────────┐                                    ┌──────────────────┐
│             │  1. Request PKCS7 signature        │                  │
│ EC2         ├───────────────────────────────────>│   EC2 IMDS       │
│ Instance    │  2. Return PKCS7 signature         │   (169.254...)   │
│             │<───────────────────────────────────┤                  │
└──────┬──────┘                                    └──────────────────┘
       │
       │ 3. Send request with:
       │    - pkcs7 (signature only)
       │    - timestamp
       │    - errorMessage
       │
       │    SECURITY: instanceId and document are NOT sent.
       │    They are extracted from PKCS7 on the server side.
       │
       v
┌──────────────────────────────────────────────────────────────────┐
│                     Ocean Current API                             │
│                                                                   │
│  ┌────────────────────────────────────────────────────────┐     │
│  │  Ec2InstanceAuthenticationFilter                       │     │
│  │  - Extract pkcs7 from request body                     │     │
│  │  - Pass to Ec2InstanceIdentityValidator                │     │
│  └────────────┬───────────────────────────────────────────┘     │
│               │                                                   │
│               v                                                   │
│  ┌────────────────────────────────────────────────────────┐     │
│  │  Ec2InstanceIdentityValidator                          │     │
│  │  1. Verify PKCS7 signature (cryptographic proof)       │     │
│  │  2. Extract document from PKCS7 signature              │     │
│  │  3. Extract instanceId from document                   │     │
│  │  4. Validate request timestamp (optional, if provided) │     │
│  │  5. Check instanceId against whitelist                 │     │
│  └────────────┬───────────────────────────────────────────┘     │
│               │                                                   │
│               v                                                   │
│  ┌────────────────────────────────────────────────────────┐     │
│  │  ✓ Authenticated → Allow request                       │     │
│  │  ✗ Failed → Return 401 Unauthorized                    │     │
│  └────────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────┘
```

### End-to-end Flow

```
┌─────────────────┐                    ┌──────────────────┐
│   EC2 Instance  │                    │  Ocean Current   │
│  (Your Server)  │                    │     Backend      │
└────────┬────────┘                    └────────┬─────────┘
         │                                      │
         │ 1. Fetch PKCS7 signature             │
         │    (from metadata service)           │
         │                                      │
         │ 2. POST /monitoring/fatal-log        │
         │    {pkcs7, timestamp, errorMessage}  │
         ├─────────────────────────────────────>│
         │                                      │
         │                              3. Verify PKCS7 signature
         │                              4. Extract document from PKCS7
         │                              5. Extract instance ID from document
         │                              6. Validate request timestamp (if provided)
         │                              7. Check whitelist
         │                                      │
         │ 8. Response (200 OK or 401)         │
         │<─────────────────────────────────────┤
         │                                      │
```

### What the Backend Does

- **`Ec2InstanceAuthenticationFilter`** (prod/edge only) intercepts `/api/v1/monitoring/**`:

  - Parses the JSON body into `MonitoringRequest`
  - Checks `pkcs7` is present
  - Uses **`Ec2InstanceIdentityValidator`** to:
    - Verify the PKCS7 signature is valid and signed by AWS
    - Extract the instance identity document from the PKCS7 signature
    - Extract the instance ID from the document
    - Validate the request timestamp (if provided) to prevent replay attacks
  - Verifies the extracted `instanceId` is in the **whitelist** (`AUTHORISED_INSTANCE_IDS`)
  - Logs `[MONITORING-AUTH-SUCCESS]` / `[MONITORING-AUTH-FAILED]`

- **`MonitoringController`** then logs a `[FATAL]` message that monitoring systems (e.g. NewRelic) can pick up.

Authentication is **only enforced in `prod` and `edge` profiles**; in local/dev you can call the endpoint without EC2 metadata.

## Security Features

### 1. Cryptographic Verification

The PKCS7 signature is created by AWS using their private key and can only be verified using AWS's public certificate. This provides:

- **Authenticity**: The document genuinely comes from AWS
- **Integrity**: The document hasn't been tampered with
- **Non-repudiation**: AWS is the only party that can create valid signatures

### 2. Server-Side Document Extraction

**Critical Security Feature**: Only the PKCS7 signature is sent in requests. The server extracts the instance identity document and instance ID from the PKCS7 signature during validation. This prevents:

- **Tampering**: Clients cannot modify the instance ID or document
- **Forgery**: The instance ID must match what's embedded in the signed PKCS7
- **Manipulation**: Any attempt to send a different instance ID is impossible

### 3. Certificate Pinning

We pin the AWS EC2 instance identity certificate by loading a trusted certificate and using it directly to verify signatures:

```java
// Load the pinned AWS EC2 identity certificate
X509Certificate awsIdentityCert = loadAwsIdentityCertificate();

// Verify the signature with the pinned AWS certificate
if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(awsIdentityCert))) {
    // Signature verified with pinned certificate
    signatureVerified = true;
}
```

**How it works:**

- The AWS EC2 identity certificate is loaded from a trusted source (classpath or filesystem)
- This certificate is used directly to verify the PKCS7 signature
- We don't trust certificates embedded in the PKCS7 signature - only the pre-loaded pinned certificate
- This prevents attacks using rogue certificates, even if an attacker could somehow get a certificate into the PKCS7 signature

### 4. Instance ID Whitelist

Only EC2 instances with specific instance IDs (configured in `AUTHORISED_INSTANCE_IDS`) are allowed access. This provides:

- **Access control**: Even with valid signatures, only specific instances can authenticate
- **Audit trail**: We know exactly which instances are allowed

### 5. Request Timestamp Validation (Replay Attack Prevention)

The request timestamp (from the request body) is validated if provided:

```java
private ValidationResult validateRequestTimestamp(String requestTimestamp) {
    Instant timestamp = Instant.parse(requestTimestamp);
    Instant now = Instant.now();
    long ageSeconds = now.getEpochSecond() - timestamp.getEpochSecond();

    // Reject requests older than timestampToleranceSeconds (default: 300s = 5 minutes)
    if (ageSeconds > timestampToleranceSeconds) {
        return ValidationResult.failure("Request timestamp too old");
    }

    // Prevent future timestamps (clock skew protection)
    if (ageSeconds < -60) {
        return ValidationResult.failure("Request timestamp is from the future");
    }

    return ValidationResult.success(null);
}
```

**Important Notes:**

- The request timestamp validation is **optional** - if the `timestamp` field is not provided in the request, validation is skipped
- The `pendingTime` field in the instance identity document is **not validated** - it represents when the instance was launched, not when the request was made
- This validation ensures the request is recent and hasn't been reused from an old authentication
- Old requests (older than 5 minutes by default) are rejected if timestamp is provided

### 6. Defense in Depth

Multiple layers of security work together:

1. **Network**: HTTPS encryption for data in transit
2. **Signature**: PKCS7 cryptographic verification
3. **Extraction**: Server extracts document from PKCS7 (prevents tampering)
4. **Timestamp**: Replay attack prevention
5. **Whitelist**: Instance-level access control
6. **Profile**: Only active in prod/edge environments

## Architecture

### Components

#### 1. Ec2InstanceIdentityValidator

**Location**: [`src/main/java/au/org/aodn/oceancurrent/security/Ec2InstanceIdentityValidator.java`](../src/main/java/au/org/aodn/oceancurrent/security/Ec2InstanceIdentityValidator.java)

**Responsibilities**:

- Load AWS EC2 identity certificate
- Verify PKCS7 signatures using BouncyCastle
- Extract instance identity document from PKCS7 signature
- Extract instance ID from document
- Validate request timestamp (if provided) to prevent replay attacks
- Certificate pinning by using pre-loaded trusted AWS certificate

**Key Methods**:

- `validatePkcs7Only(pkcs7Signature, requestTimestamp)`: Main validation entry point (extracts document and instanceId from PKCS7)
- `verifyAndExtractSignedDocument()`: Cryptographic signature verification and document extraction
- `parseDocument()`: Parse JSON document into DTO
- `validateRequestTimestamp()`: Optional replay attack prevention (validates request timestamp if provided)
- `extractInstanceIdFromDocument()`: Extract instance ID from parsed document

#### 2. Ec2InstanceAuthenticationFilter

**Location**: [`src/main/java/au/org/aodn/oceancurrent/security/Ec2InstanceAuthenticationFilter.java`](../src/main/java/au/org/aodn/oceancurrent/security/Ec2InstanceAuthenticationFilter.java)

**Responsibilities**:

- Intercept requests to `/api/v1/monitoring` endpoints
- Parse request body into `MonitoringRequest` DTO
- Extract `pkcs7` and optional `timestamp` from request body
- Invoke `Ec2InstanceIdentityValidator.validatePkcs7()` with PKCS7 signature and timestamp
- Check extracted instance ID against whitelist
- Return 401 Unauthorized if validation fails

**Active Profiles**: `prod`, `edge` only (disabled in dev/test)

#### 3. MonitoringSecurityProperties

**Location**: [`src/main/java/au/org/aodn/oceancurrent/configuration/MonitoringSecurityProperties.java`](../src/main/java/au/org/aodn/oceancurrent/configuration/MonitoringSecurityProperties.java)

**Configuration Properties**:

- `app.monitoring-security.authorised-instance-ids`: Comma-separated list of allowed EC2 instance IDs
- `app.ec2-identity-cert-path`: Path to AWS EC2 certificate (default: `classpath:aws/ec2-instance-identity.pem`)
- `app.ec2-identity-timestamp-tolerance-seconds`: Maximum age for request timestamp validation (default: 300 seconds)

#### 4. Python Client Script

**Location**: [`scripts/trigger_fatal_log.py`](../scripts/trigger_fatal_log.py)

**Features**:

- IMDSv2 support with IMDSv1 fallback
- Fetches PKCS7 signature from EC2 metadata service
- Sends only PKCS7 signature, timestamp, and errorMessage in requests
- Does not send instanceId or document (extracted server-side)

## Configuration

### 1. Environment Variables

```bash
# Required: Comma-separated list of authorized EC2 instance IDs
export AUTHORISED_INSTANCE_IDS="i-0123456789abcdef0,i-0987654321fedcba0"

# Optional: Custom certificate path
export EC2_IDENTITY_CERT_PATH="/path/to/ec2-instance-identity.pem"

# Optional: Custom timestamp tolerance (in seconds)
export EC2_IDENTITY_TIMESTAMP_TOLERANCE_SECONDS="300"
```

### 2. Application Configuration

**File**: `src/main/resources/application.yaml`

```yaml
app:
  monitoring-security:
    authorised-instance-ids: ${AUTHORISED_INSTANCE_IDS:}
  ec2-identity-cert-path: ${EC2_IDENTITY_CERT_PATH:classpath:aws/ec2-instance-identity.pem}
  ec2-identity-timestamp-tolerance-seconds: ${EC2_IDENTITY_TIMESTAMP_TOLERANCE_SECONDS:300}
```

### 3. AWS EC2 Certificate

The AWS EC2 instance identity certificate is included in the project at:

```
src/main/resources/aws/ec2-instance-identity.pem
```

This certificate is:

- **Public**: Safe to include in source control
- **Standard**: Same for all AWS regions
- **Long-lived**: Valid until 2038
- **Official**: From AWS documentation

**Certificate Details**:

- Subject: C=US, ST=Washington State, L=Seattle, O=Amazon Web Services LLC
- Validity: 2012-01-05 to 2038-01-05
- Algorithm: DSA with SHA1

### 4. Whitelisted Instance IDs

The whitelisted instance IDs are configured via the `AUTHORISED_INSTANCE_IDS` environment variable and passed to the application at container startup (not hardcoded in the repository).

#### How it works

1. **Dockerfile** passes the environment variable to Spring Boot:

   ```
   -Dapp.monitoring-security.authorised-instance-ids=${AUTHORISED_INSTANCE_IDS}
   ```

2. **Configuration** in `application.yaml` / `application-prod.yaml` / `application-edge.yaml`:

   ```yaml
   app:
     monitoring-security:
       authorised-instance-ids: ${AUTHORISED_INSTANCE_IDS:} # Comma-separated instance IDs
   ```

3. **MonitoringSecurityProperties** class loads these values at runtime

#### Deploying to production/edge

To authorize EC2 instances, set the `AUTHORISED_INSTANCE_IDS` environment variable when deploying:

**Format**: Comma-separated EC2 instance IDs (no spaces)

```text
AUTHORISED_INSTANCE_IDS=i-prod-instance-1,i-prod-instance-2,i-prod-instance-3
```

**GitHub Actions / AWS ECS**: Pass the variable when deploying the container:

```bash
# Example ECS task definition environment variable
AUTHORISED_INSTANCE_IDS=i-0123456789abcdef0,i-0123456789abcdef1
```

To find instance IDs:

```bash
aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=your-instance-name" \
  --query 'Reservations[*].Instances[*].InstanceId' \
  --output text
```

**No code changes or redeployment needed** – just update the environment variable in your deployment configuration (GitHub Actions secrets/variables, AWS Systems Manager Parameter Store, or ECS task definition).

### 5. Active Profiles

```java
@Profile({"prod", "edge"})
public class Ec2InstanceAuthenticationFilter extends OncePerRequestFilter {
    // ...
}
```

- **prod/edge**: full authentication (PKCS7 + whitelist) is enforced.
- **local/dev**: the filter is inactive; you can call the endpoint without EC2 metadata:

```bash
curl -X POST http://localhost:8080/api/v1/monitoring/fatal-log \
  -H "Content-Type: application/json" \
  -d '{"errorMessage": "Test error from local"}'
```

## Usage

### From EC2 Instance (Python)

#### Installation

```bash
# Install required package
pip install requests

# Or use pip3
pip3 install requests
```

#### Basic Usage

```bash
# Set the API endpoint
export API_ENDPOINT="https://api.example.com/api/v1/monitoring/fatal-log"

# Trigger a fatal log
python3 trigger_fatal_log.py "Cron job failed: file scan timeout"
```

#### Inline Usage

```bash
API_ENDPOINT="https://your-api.com/api/v1/monitoring/fatal-log" \
python3 trigger_fatal_log.py "Daily backup completed successfully"
```

#### Cron Job Example

```bash
# Add to crontab
0 2 * * * /usr/bin/python3 /path/to/trigger_fatal_log.py "Daily health check" || echo "Health check failed"
```

### Request Format

The Python script sends requests in this format:

```json
{
  "pkcs7": "MIAGCSqGSIb3DQEHAqCAMIACAQExCzAJBgUrDgMCGgUAMIAGCSqGSIb3DQEHAaCAJIAEggHc...",
  "timestamp": "2025-12-02T12:00:00Z",
  "errorMessage": "Cron job failed: file scan timeout"
}
```

**Required fields**:

- `pkcs7` – PKCS7 signature (as returned by EC2 metadata service)

**Optional fields**:

- `timestamp` – ISO 8601 timestamp (if omitted, the server still logs with its own timestamp)
- `errorMessage` – message to log (helper script requires this as its CLI argument)
- `source` – identifier such as script or cron name
- `context` – additional free‑form metadata

**Security Note**: The `instanceId` and `document` are **NOT** sent in the request. They are extracted from the PKCS7 signature on the server side during validation. This prevents tampering.

### Alternative: Manual cURL Request

If you don't want to use Python:

```bash
#!/bin/bash

API_ENDPOINT="https://api.example.edge.com/api/v1/monitoring/fatal-log"

# 1. Fetch PKCS7 signature from metadata service
PKCS7=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/pkcs7)

# 2. Send request (only PKCS7 signature is required)
curl -X POST "$API_ENDPOINT" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "pkcs7": "$PKCS7",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "errorMessage": "Your error message here"
}
EOF
```

### Response Format

**Success (200 OK)**:

```json
{
  "status": "success",
  "message": "Fatal log generated successfully for monitoring purposes",
  "timestamp": "2025-12-02T12:00:00.123Z",
  "logLevel": "FATAL",
  "loggedError": "Cron job failed: file scan timeout"
}
```

**Unauthorized (401 Unauthorized)**:

```json
{
  "message": "Unauthorized",
  "errors": ["Invalid PKCS7 signature"]
}
```

The `message` will vary, e.g. missing pkcs7 field, invalid signature, instance not whitelisted, or timestamp validation failure.

## Testing

### Unit Tests

Run the validator unit tests:

```bash
./gradlew test --tests Ec2InstanceIdentityValidatorTest
```

### Integration Tests

Run the integration tests (requires `edge` profile):

```bash
./gradlew test --tests Ec2InstanceAuthenticationFilterIntegrationTest
```

### Manual Testing from EC2

1. **SSH into your EC2 instance**

2. **Install Python and requests**:

   ```bash
   sudo yum install python3 python3-pip -y  # Amazon Linux
   # OR
   sudo apt-get install python3 python3-pip -y  # Ubuntu

   pip3 install requests
   ```

3. **Download the script**:

   ```bash
   wget https://raw.githubusercontent.com/your-repo/scripts/trigger_fatal_log.py
   chmod +x trigger_fatal_log.py
   ```

4. **Test the script**:
   ```bash
   export API_ENDPOINT="https://your-api.com/api/v1/monitoring/fatal-log"
   python3 trigger_fatal_log.py "Test message from EC2"
   ```

### Testing Locally (Development)

The authentication filter is **disabled** in `dev` and `test` profiles. To test locally without EC2:

```bash
# Start the application in dev mode
./gradlew bootRun --args='--spring.profiles.active=dev'

# Make a request without authentication (will succeed)
curl -X POST http://localhost:8080/api/v1/monitoring/fatal-log \
  -H "Content-Type: application/json" \
  -d '{"errorMessage": "Test from local dev"}'
```

### Testing with Local Script

Use the test script to verify authentication:

```bash
# Start the app with edge profile (includes auth filter)
SPRING_PROFILES_ACTIVE=edge ./gradlew bootRun

# Test authentication enforcement
python3 scripts/test_local_auth.py

# Test authentication with actual EC2 metadata (on EC2 instance)
python3 scripts/test_local_auth.py --use-ec2-metadata
```

## Troubleshooting

### Common Issues

#### 1. "PKCS7 signature verification failed"

**Cause**: The signature is invalid or doesn't match the embedded document.

**Solutions**:

- Ensure the PKCS7 signature is from the EC2 metadata service
- Check that the certificate file exists and is correct
- Verify the signature wasn't modified or truncated
- Ensure the signature is sent exactly as returned by the metadata service

#### 2. "Request timestamp too old"

**Cause**: The request timestamp (if provided) is older than the configured tolerance.

**Solutions**:

- Check if the EC2 instance clock is synchronized (NTP)
- Ensure the `timestamp` field in the request is recent (within the tolerance window)
- Increase `EC2_IDENTITY_TIMESTAMP_TOLERANCE_SECONDS` if needed (default: 300 seconds)
- Note: If you don't provide a `timestamp` field, this validation is skipped

#### 3. "Instance not authorised"

**Cause**: The instance ID (extracted from PKCS7) is not in the whitelist.

**Solutions**:

- Add the instance ID to `AUTHORISED_INSTANCE_IDS` environment variable
- Restart the application after updating the environment variable
- Verify the instance ID matches exactly (no extra spaces)

#### 4. "Timeout fetching EC2 metadata"

**Cause**: Not running on an EC2 instance or metadata service is blocked.

**Solutions**:

- Verify you're running on an actual EC2 instance
- Check security groups allow outbound traffic to `169.254.169.254`
- Check if IMDSv2 is required (script handles this automatically)
- Confirm: `curl -s http://169.254.169.254/latest/meta-data/instance-id`

#### 5. "Certificate not found"

**Cause**: The AWS EC2 certificate file is missing.

**Solutions**:

- Verify `src/main/resources/aws/ec2-instance-identity.pem` exists
- Check the `EC2_IDENTITY_CERT_PATH` environment variable
- Ensure the file is included in the JAR/build output

#### 6. "Missing pkcs7 field"

**Cause**: The request body doesn't include the required `pkcs7` field.

**Solutions**:

- Ensure the request includes the `pkcs7` field
- Verify the PKCS7 signature is fetched from the EC2 metadata service
- Check that the request body is valid JSON

### Debugging

Enable debug logging for detailed validation information:

**application.yaml**:

```yaml
logging:
  level:
    au.org.aodn.oceancurrent.security: DEBUG
```

**Log output example**:

```
[INSTANCE-IDENTITY-VALIDATION] Loaded AWS EC2 identity certificate from classpath:aws/ec2-instance-identity.pem
[INSTANCE-IDENTITY-VALIDATION] Successfully extracted signed document from PKCS7
[INSTANCE-IDENTITY-VALIDATION] Signature verified with pinned AWS certificate | Certificate=CN=...
[INSTANCE-IDENTITY-VALIDATION] Request timestamp validation passed | Timestamp=2025-12-02T10:00:00Z | AgeSeconds=45
[INSTANCE-IDENTITY-VALIDATION] Successfully validated instance identity | InstanceId=i-0123456789abcdef0
[MONITORING-AUTH-SUCCESS] EC2 instance authenticated | InstanceId=i-0123456789abcdef0 | IP=10.0.1.50
```

### Validation Checklist

When requests fail, verify:

- [ ] Application is running with `prod` or `edge` profile
- [ ] Instance ID (extracted from PKCS7) is in `AUTHORISED_INSTANCE_IDS`
- [ ] Certificate file exists at configured path
- [ ] Request includes the required `pkcs7` field
- [ ] PKCS7 signature is from EC2 metadata service (not modified)
- [ ] Request timestamp (if provided) is recent (< 5 minutes old by default)
- [ ] EC2 instance has network access to metadata service
- [ ] Python script has `requests` library installed

## Security Considerations

### What This Protects Against

✅ **Unauthorized access**: Only whitelisted EC2 instances can access monitoring endpoints
✅ **Credential forgery**: PKCS7 signature ensures authenticity
✅ **Document tampering**: Server extracts document from PKCS7, preventing client-side manipulation
✅ **Instance ID tampering**: Instance ID is extracted server-side from PKCS7, cannot be forged
✅ **Replay attacks**: Optional request timestamp validation prevents reuse of old requests (if timestamp is provided)
✅ **Man-in-the-middle**: HTTPS protects data in transit

### What This Does NOT Protect Against

❌ **Compromised EC2 instances**: If an attacker gains access to an authorized instance, they can authenticate
❌ **Insider threats**: Legitimate instance owners can access the endpoint
❌ **DoS attacks**: Rate limiting should be implemented separately

### Best Practices

1. **Minimize instance whitelist**: Only add instances that genuinely need access
2. **Use IMDSv2**: The Python script uses IMDSv2 by default (more secure than IMDSv1)
3. **Monitor logs**: Watch for authentication failures (possible attack indicators)
4. **Rotate instances**: Regularly update your EC2 instances and remove old instance IDs from whitelist
5. **Principle of least privilege**: Only grant monitoring endpoint access to instances that need it
6. **Network segmentation**: Use security groups to restrict which instances can reach your API
7. **Server-side extraction**: Always extract instance ID and document from PKCS7 on the server (never trust client-provided values)

## Endpoint URLs

- **Production**: `https://api.example.com/api/v1/monitoring/fatal-log`
- **Edge (staging)**: `https://api.example.edge.com/api/v1/monitoring/fatal-log`
- **Local dev**: `http://localhost:8080/api/v1/monitoring/fatal-log`

## References

- [AWS EC2 Instance Identity Documents](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instance-identity-documents.html)
- [Verifying Instance Identity Signatures](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/verify-signature.html)
- [IMDSv2 (Instance Metadata Service Version 2)](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html)
- [BouncyCastle CMS/PKCS7](https://www.bouncycastle.org/documentation.html)

## License

This authentication system is part of the Ocean Current API project.
