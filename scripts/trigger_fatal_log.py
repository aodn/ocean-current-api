#!/usr/bin/env python3
"""
EC2 Instance Fatal Log Trigger

Minimal script to send fatal error notifications from EC2 instances to Ocean Current API.
Uses EC2 instance identity document and PKCS7 signature for authentication.

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
import json
import requests
from datetime import datetime

# EC2 Instance Metadata Service endpoints
METADATA_BASE_URL = "http://169.254.169.254/latest"
INSTANCE_IDENTITY_DOCUMENT_URL = f"{METADATA_BASE_URL}/dynamic/instance-identity/document"
INSTANCE_IDENTITY_PKCS7_URL = f"{METADATA_BASE_URL}/dynamic/instance-identity/pkcs7"

# Backend API endpoint from environment variable
API_ENDPOINT = os.environ.get("API_ENDPOINT")

# Metadata service timeout
METADATA_TIMEOUT = 2  # seconds


def fetch_instance_identity():
    """
    Fetch instance identity document and PKCS7 signature from EC2 metadata service.

    Returns:
        Tuple of (instance_id, document, pkcs7_signature) or (None, None, None) on error
    """
    try:
        # Fetch instance identity document
        doc_response = requests.get(
            INSTANCE_IDENTITY_DOCUMENT_URL,
            timeout=METADATA_TIMEOUT
        )
        doc_response.raise_for_status()
        document = doc_response.text

        # Parse document to extract instance ID
        doc_json = json.loads(document)
        instance_id = doc_json.get("instanceId")

        if not instance_id:
            print("ERROR: Could not extract instanceId from metadata document", file=sys.stderr)
            return None, None, None

        # Fetch PKCS7 signature
        pkcs7_response = requests.get(
            INSTANCE_IDENTITY_PKCS7_URL,
            timeout=METADATA_TIMEOUT
        )
        pkcs7_response.raise_for_status()
        pkcs7_signature = pkcs7_response.text

        print(f"Successfully fetched instance identity: {instance_id}")
        return instance_id, document, pkcs7_signature

    except requests.exceptions.Timeout:
        print("ERROR: Timeout fetching EC2 metadata. Are you running on an EC2 instance?", file=sys.stderr)
        return None, None, None
    except requests.exceptions.RequestException as e:
        print(f"ERROR: Failed to fetch EC2 metadata: {e}", file=sys.stderr)
        return None, None, None
    except json.JSONDecodeError as e:
        print(f"ERROR: Failed to parse instance identity document: {e}", file=sys.stderr)
        return None, None, None


def send_fatal_log(error_message, instance_id, document, pkcs7):
    """
    Send fatal log request to the backend API.

    Args:
        error_message: The error message to log
        instance_id: EC2 instance ID
        document: Instance identity document JSON
        pkcs7: PKCS7 signature

    Returns:
        True if successful, False otherwise
    """
    # Build request payload
    payload = {
        "instanceId": instance_id,
        "document": document,
        "pkcs7": pkcs7,
        "timestamp": datetime.now().isoformat() + "Z",
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
    instance_id, document, pkcs7 = fetch_instance_identity()

    if not all([instance_id, document, pkcs7]):
        print("\n✗ Failed to fetch instance identity. Exiting.", file=sys.stderr)
        sys.exit(1)

    # Send fatal log to backend
    success = send_fatal_log(
        error_message=error_message,
        instance_id=instance_id,
        document=document,
        pkcs7=pkcs7
    )

    print("=" * 60)

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
