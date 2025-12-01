# EC2 Instance Authentication – Fatal Log Endpoint

## Overview

The Ocean Current API secures the `/api/v1/monitoring/fatal-log` endpoint using the **EC2 instance identity document + PKCS7 signature**.
Only EC2 instances you explicitly whitelist can trigger fatal logs for monitoring purposes.

### End-to-end flow

```
┌─────────────────┐                    ┌──────────────────┐
│   EC2 Instance  │                    │  Ocean Current   │
│  (Your Server)  │                    │     Backend      │
└────────┬────────┘                    └────────┬─────────┘
         │                                      │
         │ 1. Fetch identity document          │
         │    + PKCS7 signature                 │
         │    (from metadata service)           │
         │                                      │
         │ 2. POST /monitoring/fatal-log        │
         │    {instanceId, document, pkcs7}     │
         ├─────────────────────────────────────>│
         │                                      │
         │                              3. Verify PKCS7 signature
         │                              4. Validate instance ID
         │                              5. Check whitelist
         │                                      │
         │ 6. Response (200 OK or 401)          │
         │<─────────────────────────────────────┤
         │                                      │
```

### What the backend does

- **`Ec2InstanceAuthenticationFilter`** (prod/edge only) intercepts `/api/v1/monitoring/**`:

  - Parses the JSON body into `MonitoringRequest`
  - Checks `instanceId`, `document`, and `pkcs7` are present
  - Uses **`AwsInstanceIdentityValidator`** to:
    - Verify the PKCS7 signature is valid and signed by AWS
    - Confirm the instance identity document is authentic
    - Ensure the document’s `instanceId` matches the claimed `instanceId`
  - Verifies `instanceId` is in the **whitelist** (`ALLOWED_INSTANCE_IDS`)
  - Logs `[MONITORING-AUTH-SUCCESS]` / `[MONITORING-AUTH-FAILED]`

- **`MonitoringController`** then logs a `[FATAL]` message that monitoring systems (e.g. NewRelic) can pick up.

Authentication is **only enforced in `prod` and `edge` profiles**; in local/dev you can call the endpoint without EC2 metadata.

## How to call the endpoint from EC2

### Recommended: Python helper script

Use the minimal helper script in `scripts/trigger_fatal_log.py`. It:

- Fetches the **instance identity document** and **PKCS7 signature** from the EC2 metadata service
- Extracts the **instance ID**
- Sends a JSON POST request to the monitoring endpoint

#### One‑time setup on the instance

```bash
pip3 install requests
```

Set the API endpoint (edge/prod/local) and send a fatal log:

```bash
export API_ENDPOINT="https://api.example.edge.com/api/v1/monitoring/fatal-log"
python3 /path/to/trigger_fatal_log.py "File scan failed while generating JSON index"
```

Notes:

- The first argument is the **error message** that will be logged at FATAL level.
- The script automatically adds `instanceId`, `document`, `pkcs7`, and a `timestamp`.

### Alternative: Manual cURL request

If you don’t want to use Python:

```bash
#!/bin/bash

API_ENDPOINT="https://api.example.edge.com/api/v1/monitoring/fatal-log"

# 1. Fetch instance identity document and PKCS7 signature from metadata service
DOCUMENT=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document)
PKCS7=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/pkcs7)

# 2. Extract instance ID
INSTANCE_ID=$(echo "$DOCUMENT" | jq -r '.instanceId')

# 3. Escape document JSON for embedding
DOCUMENT_ESCAPED=$(echo "$DOCUMENT" | jq -c '.')

# 4. Send request
curl -X POST "$API_ENDPOINT" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "instanceId": "$INSTANCE_ID",
  "document": "$DOCUMENT_ESCAPED",
  "pkcs7": "$PKCS7",
  "errorMessage": "Your error message here"
}
EOF
```

## Request & response shapes

### Request body

```json
{
  "instanceId": "i-0123456789abcdef0",
  "document": "{\"instanceId\":\"i-0123456789abcdef0\",\"region\":\"ap-southeast-2\",...}",
  "pkcs7": "MIAGCSqGSIb3DQEHAqCAMIACAQExCzAJBgUrDgMCGgUAMIAGCSqGSIb3...",
  "timestamp": "2025-11-28T12:00:00Z",
  "errorMessage": "File scan failed after 3 retries while generating JSON index",
  "source": "data-sync-job",
  "context": "exit_code=1, last_run=2025-11-27T12:00:00Z"
}
```

- **Required**:
  - `instanceId` – EC2 instance ID (from metadata service)
  - `document` – instance identity document JSON (as returned by metadata service)
  - `pkcs7` – PKCS7 signature (as returned by metadata service)
- **Optional**:
  - `timestamp` – ISO 8601 timestamp (if omitted, the server still logs with its own timestamp)
  - `errorMessage` – message to log (helper script requires this as its CLI argument)
  - `source` – identifier such as script or cron name
  - `context` – additional free‑form metadata

### Successful response (200)

```json
{
  "status": "success",
  "message": "Fatal log generated successfully for monitoring purposes",
  "timestamp": "2025-11-28T12:34:56.789Z",
  "logLevel": "FATAL",
  "loggedError": "File scan failed after 3 retries while generating JSON index"
}
```

### Authentication failure (401)

```json
{
  "error": "Unauthorised",
  "message": "Invalid instance identity: PKCS7 signature verification failed"
}
```

The `message` will vary, e.g. missing fields, invalid signature, instance not whitelisted, or instance ID mismatch.

## Backend configuration

### Whitelisted instance IDs

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
       authorized-instance-ids: ${AUTHORIZED_INSTANCE_IDS:} # Comma-separated instance IDs
   ```

3. **MonitoringSecurityProperties** class loads these values at runtime

#### Deploying to production/edge

To authorize EC2 instances, set the `AUTHORIZED_INSTANCE_IDS` environment variable when deploying:

**Format**: Comma-separated EC2 instance IDs (no spaces)

```text
AUTHORIZED_INSTANCE_IDS=i-prod-instance-1,i-prod-instance-2,i-prod-instance-3
```

**GitHub Actions / AWS ECS**: Pass the variable when deploying the container:

```bash
# Example ECS task definition environment variable
AUTHORIZED_INSTANCE_IDS=i-0123456789abcdef0,i-0123456789abcdef1
```

To find instance IDs:

```bash
aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=your-instance-name" \
  --query 'Reservations[*].Instances[*].InstanceId' \
  --output text
```

**No code changes or redeployment needed** – just update the environment variable in your deployment configuration (GitHub Actions secrets/variables, AWS Systems Manager Parameter Store, or ECS task definition).

### Active profiles

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

## Common issues

- **“Timeout fetching EC2 metadata” (from the Python script)**

  - Not running on EC2, or metadata service is blocked.
  - Confirm: `curl -s http://169.254.169.254/latest/meta-data/instance-id`.

- **“Invalid PKCS7 signature”**

  - Signature or document was modified, truncated, or whitespace‑mangled.
  - Always send `document` and `pkcs7` exactly as returned by the metadata service.

- **“Instance not authorized”**

  - `instanceId` is not in `ALLOWED_INSTANCE_IDS`.
  - Add the instance ID to the whitelist and redeploy.

- **“Instance ID mismatch”**
  - The `instanceId` in the body doesn’t match the one inside `document`.
  - Always derive `instanceId` from the same `document` you send.

## Endpoint URLs

- **Production**: `https://api.example.com/api/v1/monitoring/fatal-log`
- **Edge (staging)**: `https://api.example.edge.com/api/v1/monitoring/fatal-log`
- **Local dev**: `http://localhost:8080/api/v1/monitoring/fatal-log`

For deeper background, see:

- AWS docs: EC2 instance metadata service and instance identity documents
- PKCS7 / CMS signature verification concepts
