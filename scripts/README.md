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

### Configuration

The script reads the API endpoint from a config file or environment variable (config file takes precedence):

**Option 1: Config file (recommended for production)**

```bash
# System-wide configuration
sudo mkdir -p /etc/imos
echo "https://api.example.com/api/v1/monitoring/fatal-log" | sudo tee /etc/imos/oc_api_endpoint.conf

# OR local configuration (in same directory as script)
echo "https://api.example.com/api/v1/monitoring/fatal-log" > ./scripts/oc_api_endpoint.conf
```

**Option 2: Environment variable (fallback)**

```bash
export OC_API_ENDPOINT="https://api.example.com/api/v1/monitoring/fatal-log"
```

### Basic usage (EC2 instance)

1. Run the script with your error message:

```bash
python3 trigger_fatal_log.py "File scan failed while generating JSON index"
```

2. **Optional**: Add source type and additional context for better categorization:

```bash
# With source type only
python3 trigger_fatal_log.py "Configuration error" "startup"

# With source type and additional context
python3 trigger_fatal_log.py "Database connection failed" "test" "db=postgres,timeout=30s"
```

The script will:

- Fetch the instance identity document and PKCS7 signature from the EC2 metadata service
- POST a JSON body containing `pkcs7`, `timestamp`, `errorMessage`, `source`, and `context`
- Include metadata: script name, process ID, Python version in the `context` field

### Local testing

For local/dev testing (no EC2 metadata, no auth filter):

```bash
export OC_API_ENDPOINT="http://localhost:8080/api/v1/monitoring/fatal-log"
python3 trigger_fatal_log.py "Test error from local"
```

> In non‑prod profiles the EC2 authentication filter is disabled, so only the `errorMessage` is used.

## Additional docs

For the full authentication and backend flow, see `docs/EC2_INSTANCE_AUTHENTICATION.md`.
