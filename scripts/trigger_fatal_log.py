#!/usr/bin/env python3
"""
EC2 Instance Fatal Log Trigger

Minimal script to send fatal error notifications from EC2 instances to Ocean Current API.
Uses EC2 instance identity document and PKCS7 signature for authentication.
Supports IMDSv2 (Instance Metadata Service Version 2) for enhanced security.

Usage (from EC2 instance):
    API_ENDPOINT="https://api.example.com/api/v1/monitoring/fatal-log" \\
    python3 trigger_fatal_log.py "Error message"

Requirements:
    - Python 3.6+
    - requests: pip install requests
    - Environment variable: API_ENDPOINT (monitoring endpoint URL)

Examples:
    # Set endpoint and run
    export API_ENDPOINT="https://api.example.com/api/v1/monitoring/fatal-log"
    python3 trigger_fatal_log.py "File scan failed while generating JSON index"

    # Or inline
    API_ENDPOINT="https://api.example.com/api/v1/monitoring/fatal-log" \\
    python3 trigger_fatal_log.py "Daily backup completed"
"""

import sys
import os
import requests
from datetime import datetime

# EC2 Instance Metadata Service endpoints (IMDSv2)
METADATA_BASE_URL = "http://169.254.169.254/latest"
IMDS_TOKEN_URL = f"{METADATA_BASE_URL}/api/token"
INSTANCE_IDENTITY_PKCS7_URL = f"{METADATA_BASE_URL}/dynamic/instance-identity/pkcs7"

# Backend API endpoint from environment variable
API_ENDPOINT = os.environ.get("API_ENDPOINT")

# Metadata service timeout
METADATA_TIMEOUT = 2  # seconds
IMDS_TOKEN_TTL_SECONDS = 21600  # 6 hours


def get_imds_token():
    """
    Fetch IMDSv2 session token for secure metadata access.

    Returns:
        Token string or None on error
    """
    try:
        response = requests.put(
            IMDS_TOKEN_URL,
            headers={"X-aws-ec2-metadata-token-ttl-seconds": str(IMDS_TOKEN_TTL_SECONDS)},
            timeout=METADATA_TIMEOUT
        )
        response.raise_for_status()
        return response.text
    except requests.exceptions.RequestException as e:
        print(f"WARNING: Failed to get IMDSv2 token, falling back to IMDSv1: {e}", file=sys.stderr)
        return None


def fetch_instance_identity():
    """
    Fetch PKCS7 signature from EC2 metadata service.
    Uses IMDSv2 for enhanced security, with fallback to IMDSv1.

    Returns:
        PKCS7 signature string or None on error
    """
    try:
        # Get IMDSv2 token (optional, will fallback to IMDSv1 if unavailable)
        token = get_imds_token()
        headers = {}
        if token:
            headers["X-aws-ec2-metadata-token"] = token
            print("Using IMDSv2 (secure mode)")
        else:
            print("Using IMDSv1 (fallback mode)")

        # Fetch PKCS7 signature
        pkcs7_response = requests.get(
            INSTANCE_IDENTITY_PKCS7_URL,
            headers=headers,
            timeout=METADATA_TIMEOUT
        )
        pkcs7_response.raise_for_status()
        pkcs7_signature = pkcs7_response.text

        print("Successfully fetched instance identity")
        return pkcs7_signature

    except requests.exceptions.Timeout:
        print("ERROR: Timeout fetching EC2 metadata. Are you running on an EC2 instance?", file=sys.stderr)
        return None
    except requests.exceptions.RequestException as e:
        print(f"ERROR: Failed to fetch EC2 metadata: {e}", file=sys.stderr)
        return None


def send_fatal_log(error_message, pkcs7):
    """
    Send fatal log request to the backend API.

    Args:
        error_message: The error message to log
        pkcs7: PKCS7 signature (instanceId and document are extracted from this on the server)

    Returns:
        True if successful, False otherwise
    """
    # Build request payload
    # SECURITY NOTE: We only send the PKCS7 signature and timestamp.
    # The instanceId and document are extracted from the PKCS7 on the server side.
    # This prevents tampering with the instance identity.
    payload = {
        "pkcs7": pkcs7,
        "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "errorMessage": error_message
    }

    try:
        print(f"Sending fatal log to {API_ENDPOINT}...")
        response = requests.post(
            API_ENDPOINT,
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=10
        )

        if response.status_code == 200:
            print("✓ Fatal log sent successfully")
            result = response.json()
            print(f"  Status: {result.get('status')}")
            print(f"  Message: {result.get('message')}")
            return True
        else:
            print(f"✗ Failed to send fatal log: HTTP {response.status_code}", file=sys.stderr)
            try:
                error_detail = response.json()
                print(f"  Error: {error_detail.get('message', 'Unknown error')}", file=sys.stderr)
            except:
                print(f"  Response: {response.text}", file=sys.stderr)
            return False

    except requests.exceptions.Timeout:
        print("✗ Request timeout - backend did not respond in time", file=sys.stderr)
        return False
    except requests.exceptions.RequestException as e:
        print(f"✗ Request failed: {e}", file=sys.stderr)
        return False


def main():
    # Validate API_ENDPOINT is configured
    if not API_ENDPOINT:
        print("ERROR: API_ENDPOINT environment variable is not set", file=sys.stderr)
        print("Example: API_ENDPOINT=\"https://api.example.com/api/v1/monitoring/fatal-log\" python3 trigger_fatal_log.py \"Error message\"", file=sys.stderr)
        sys.exit(1)

    if len(sys.argv) < 2:
        print("Usage: python3 trigger_fatal_log.py \"Error message\"", file=sys.stderr)
        print("Example: python3 trigger_fatal_log.py \"File scan failed while generating JSON index\"", file=sys.stderr)
        sys.exit(1)

    error_message = sys.argv[1]

    print("=" * 60)
    print("EC2 Instance Fatal Log Trigger")
    print("=" * 60)

    # Fetch instance identity from metadata service
    pkcs7 = fetch_instance_identity()

    if not pkcs7:
        print("\n✗ Failed to fetch instance identity. Exiting.", file=sys.stderr)
        sys.exit(1)

    # Send fatal log to backend
    success = send_fatal_log(
        error_message=error_message,
        pkcs7=pkcs7
    )

    print("=" * 60)

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
