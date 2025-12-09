# Monitoring Scripts – Quick Reference

Scripts for triggering the Ocean Current API monitoring endpoint from EC2 instances.

## Main script: `trigger_fatal_log.py`

This helper sends a POST request to `/api/v1/monitoring/fatal-log` using the EC2 **instance identity document + PKCS7 signature**.

### Prerequisites

- Python 3.6+
- `requests` library:

```bash
pip3 install requests
```

### Basic usage (EC2 instance)

1. Set the API endpoint:

```bash
export API_ENDPOINT="https://api.example.com/api/v1/monitoring/fatal-log"
```

2. Run the script with your error message:

```bash
python3 trigger_fatal_log.py "File scan failed while generating JSON index"
```

The script will:

- Fetch the instance identity document and PKCS7 signature from the EC2 metadata service
- Extract the instance ID
- POST a JSON body containing `instanceId`, `document`, `pkcs7`, `timestamp`, and your error message

### Local testing

For local/dev testing (no EC2 metadata, no auth filter):

```bash
export API_ENDPOINT="http://localhost:8080/api/v1/monitoring/fatal-log"
python3 trigger_fatal_log.py "Test error from local"
```

> In non‑prod profiles the EC2 authentication filter is disabled, so only the `errorMessage` is used.

## Additional docs

For the full authentication and backend flow, see `docs/EC2_INSTANCE_AUTHENTICATION.md`.
