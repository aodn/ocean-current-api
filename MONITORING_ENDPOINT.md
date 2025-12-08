# Monitoring Endpoint Documentation

## Overview

The monitoring endpoint allows external cron jobs to report failures and errors by generating FATAL log entries. This enables monitoring systems like NewRelic to detect issues by searching for specific log keywords and trigger alerts.

## Authentication

All monitoring endpoints require API key authentication via a custom header.

**Header Name:** `X-API-Key`
**Header Value:** The API key configured in the `MONITORING_API_KEY` environment variable

## Endpoints

### POST `/api/v1/monitoring/alert`

Reports a failure or error from an external cron job by generating a FATAL log entry.

**Request Body (JSON):**
```json
{
  "message": "Error message describing what went wrong",
  "source": "job-name-or-context"
}
```

**Fields:**
- `message` (optional): The error message describing what went wrong. Defaults to "Monitoring alert triggered" if not provided.
- `source` (optional): Context or job name (e.g., "daily-backup-job", "data-sync")

**Example Request:**
```bash
curl -X POST "https://your-api.com/api/v1/monitoring/alert" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-key" \
  -d '{
    "message": "Backup failed to upload to S3. Error: Access denied for bucket oceancurrent-backup",
    "source": "daily-backup-job"
  }'
```

**Example Response:**
```json
{
  "status": "logged",
  "timestamp": "2025-12-08T10:30:45.123Z",
  "message": "Failure logged successfully"
}
```

**Generated Log:**
```
ERROR [FATAL]: [daily-backup-job] Backup failed to upload to S3. Error: Access denied for bucket oceancurrent-backup
```

### GET `/api/v1/monitoring/ping`

Simple ping endpoint to verify the API is reachable and authentication is working.

**Example Request:**
```bash
curl -X GET "https://your-api.com/api/v1/monitoring/ping" \
  -H "X-API-Key: your-secret-key"
```

**Example Response:**
```json
{
  "status": "ok",
  "timestamp": "2025-12-08T10:30:45.123Z"
}
```

## Configuration

### Environment Variables

Add the following environment variable to your `.env` file or environment configuration:

```bash
MONITORING_API_KEY=your-secure-random-api-key-here
```

**⚠️ Important:** Use a strong, randomly generated API key in production. You can generate one using:
```bash
openssl rand -base64 32
```

### Application YAML

The monitoring configuration is already set up in `application.yaml`:
```yaml
monitoring:
  api-key: ${MONITORING_API_KEY:changeme}
```

## Python Cron Job Example

Here's an example Python script for use in a cron job that reports failures to the monitoring endpoint:

```python
#!/usr/bin/env python3
import os
import sys
import json
import requests

MONITORING_URL = "https://your-api.com/api/v1/monitoring/alert"
API_KEY = os.environ.get("MONITORING_API_KEY")
JOB_NAME = "my-cron-job"

def report_failure(error_message):
    """Report a failure to the monitoring endpoint"""
    headers = {
        "X-API-Key": API_KEY,
        "Content-Type": "application/json"
    }
    payload = {
        "message": error_message,
        "source": JOB_NAME
    }

    try:
        response = requests.post(MONITORING_URL, headers=headers, json=payload, timeout=10)
        response.raise_for_status()
        print(f"Failure reported successfully: {error_message}")
    except Exception as e:
        print(f"Failed to report to monitoring endpoint: {e}", file=sys.stderr)

def main():
    try:
        # Your cron job logic here
        perform_task()
        print("Job completed successfully")
    except Exception as e:
        error_msg = f"Job failed with error: {str(e)}"
        print(error_msg, file=sys.stderr)
        report_failure(error_msg)
        sys.exit(1)

def perform_task():
    # Example: Your actual task implementation
    # If this raises an exception, it will be caught and reported
    pass

if __name__ == "__main__":
    main()
```

### Cron Job Setup

1. Set the environment variable in your crontab:
```bash
crontab -e
```

2. Add your cron job with the API key:
```bash
MONITORING_API_KEY=your-secret-api-key

# Run every hour
0 * * * * /path/to/your/script.py >> /var/log/cron-job.log 2>&1
```

## Security Considerations

1. **API Key Storage**: Never commit the API key to version control. Use environment variables or secure secret management services.

2. **HTTPS Only**: Always use HTTPS in production to protect the API key in transit.

3. **IP Allowlisting** (Optional): Consider adding IP-based restrictions in your load balancer or security groups to only allow requests from your EC2 instance.

4. **Key Rotation**: Periodically rotate the API key. Update both the server environment variable and the cron job configuration.

## Monitoring Log Detection

Configure your monitoring system to alert on FATAL logs:

### CloudWatch Logs Filter Example
```
[level=ERROR, msg=FATAL*]
```

### DataDog Log Query Example
```
status:error "FATAL"
```

### Grep Example (for testing)
```bash
grep "FATAL" /path/to/application.log
```

## Testing

### Test Authentication
```bash
# Should return 401 Unauthorized
curl -X POST "http://localhost:8080/api/v1/monitoring/alert?message=test"

# Should return 200 OK
curl -X POST "http://localhost:8080/api/v1/monitoring/alert?message=test" \
  -H "X-API-Key: changeme"
```

### Test Ping Endpoint
```bash
curl -X GET "http://localhost:8080/api/v1/monitoring/ping" \
  -H "X-API-Key: changeme"
```

### Verify Logs
Check your application logs to see the FATAL entry:
```bash
tail -f logs/application.log | grep FATAL
```

## Troubleshooting

### 401 Unauthorized
- Check that the `X-API-Key` header is included
- Verify the API key matches the `MONITORING_API_KEY` environment variable
- Ensure the environment variable is set on the server

### 404 Not Found
- Verify the correct base path: `/api/v1/monitoring/alert`
- Check that the application is running

### No Logs Generated
- Verify the request is reaching the endpoint (check access logs)
- Ensure log level is set to ERROR or lower
- Check log file permissions

## Architecture

The monitoring endpoint uses a simple API key authentication filter that:

1. Intercepts requests to `/monitoring/*` paths
2. Validates the `X-Monitoring-Api-Key` header against the configured key
3. Returns 401 Unauthorized if the key is missing or invalid
4. Allows the request to proceed if authentication succeeds

This approach is:
- Simple to implement and maintain
- Easy for scripts to use (just add a header)
- Secure enough for internal server-to-server communication
- Stateless (no session management required)
