#!/usr/bin/env python3
"""
EC2 Instance Identity Authentication Test

Tests EC2 instance identity PKCS7 signature validation.
Authentication filter is only active in edge and prod profiles.

Usage:
    # Start the app with edge profile (includes auth filter)
    SPRING_PROFILES_ACTIVE=edge ./gradlew bootRun

    # Test authentication enforcement
    python3 scripts/test_local_auth.py

    # Test authentication with actual EC2 metadata (on EC2 instance)
    python3 scripts/test_local_auth.py --use-ec2-metadata

Requirements:
    pip3 install requests

Note:
    - Auth filter is only active with edge or prod profiles
    - Local testing requires SPRING_PROFILES_ACTIVE=edge ./gradlew bootRun
    - EC2 identity validation requires real PKCS7 signatures from EC2 metadata service
"""

import sys
import requests
import json
from datetime import datetime

# Configuration
LOCAL_URL = 'http://localhost:8080/api/v1/monitoring/fatal-log'
EC2_METADATA_URL = 'http://169.254.169.254/latest/dynamic/instance-identity'


def fetch_ec2_identity(use_ec2_metadata=False):
    """
    Fetch PKCS7 signature from EC2 metadata service.

    Args:
        use_ec2_metadata: If True, fetch from EC2 metadata service.
                         If False, return None (requires real EC2 metadata for signature).

    Returns:
        PKCS7 signature string or None if failed
    """
    if not use_ec2_metadata:
        # Cannot use simulated data - PKCS7 signatures must be cryptographically valid
        # Real signatures can only be obtained from EC2 metadata service
        print("ℹ️  PKCS7 signatures must be real and cryptographically valid")
        print("   To test EC2 identity validation:")
        print("   - Run on actual EC2 instance with --use-ec2-metadata flag")
        print("   - Authentication filter is only active in edge/prod profiles")
        return None

    print("ℹ️  Fetching EC2 identity from metadata service...")

    try:
        # Fetch PKCS7 signature
        sig_response = requests.get(
            f'{EC2_METADATA_URL}/pkcs7',
            timeout=2,
            headers={'User-Agent': 'aws-cli/2.0'}
        )

        if sig_response.status_code != 200:
            print(f"❌ Failed to fetch PKCS7 signature: {sig_response.status_code}")
            return None

        pkcs7_signature = sig_response.text.strip()
        return pkcs7_signature

    except requests.exceptions.Timeout:
        print("❌ Timeout fetching EC2 metadata (not running on EC2)")
        return None
    except requests.exceptions.ConnectionError:
        print("❌ Cannot connect to EC2 metadata service (not running on EC2)")
        return None
    except Exception as e:
        print(f"❌ Error fetching EC2 identity: {e}")
        return None


def test_without_auth():
    """Test endpoint without authentication (should be rejected on edge/prod)."""
    print("\n1. Testing without authentication:")

    try:
        response = requests.post(
            LOCAL_URL,
            json={"errorMessage": "Test without auth"},
            timeout=10
        )
        print(f"   Status: {response.status_code}")

        if response.status_code == 401:
            print("   ✅ Authentication is enforced (expected on edge/prod)")
            return True
        else:
            print(f"   ❌ Expected 401 but got {response.status_code}")
            print("      Make sure app is running with edge or prod profile")
            print("      Example: SPRING_PROFILES_ACTIVE=edge ./gradlew bootRun")
            return False

    except Exception as e:
        print(f"   ❌ Error: {e}")
        print("      Is the app running? Try: SPRING_PROFILES_ACTIVE=edge ./gradlew bootRun")
        return False


def test_with_ec2_identity(pkcs7_signature):
    """Test endpoint with EC2 instance identity authentication."""
    print("\n2. Testing with EC2 instance identity + PKCS7 signature:")

    # Build request body
    # SECURITY NOTE: We only send the PKCS7 signature and timestamp.
    # The instanceId and document are extracted from the PKCS7 on the server side.
    # This prevents tampering with the instance identity.
    request_body = {
        "pkcs7": pkcs7_signature,
        "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "errorMessage": "Local EC2 identity test"
    }

    print(f"   PKCS7 signature size: {len(pkcs7_signature)} bytes")

    try:
        response = requests.post(
            LOCAL_URL,
            json=request_body,
            headers={'Content-Type': 'application/json'},
            timeout=10
        )

        print(f"   Status: {response.status_code}")

        if response.status_code == 200:
            print("   ✅ Authentication successful!")
            try:
                print(f"\n   Response: {json.dumps(response.json(), indent=4)}")
            except:
                print(f"   Response: {response.text}")
            return True
        else:
            print(f"   ❌ Authentication failed")
            try:
                error_data = response.json()
                print(f"   Error: {error_data.get('message', error_data.get('error', response.text))}")
            except:
                print(f"   Error: {response.text}")
            return False

    except Exception as e:
        print(f"   ❌ Error: {e}")
        return False


def test_with_missing_fields():
    """Test endpoint with missing required fields."""
    print("\n3. Testing with missing required fields:")

    test_cases = [
        {
            "name": "Missing pkcs7",
            "body": {
                "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
                "errorMessage": "Test"
            }
        }
    ]

    all_passed = True
    for test_case in test_cases:
        try:
            response = requests.post(
                LOCAL_URL,
                json=test_case["body"],
                headers={'Content-Type': 'application/json'},
                timeout=10
            )

            if response.status_code == 401:
                print(f"   ✅ {test_case['name']}: Rejected (expected)")
            else:
                print(f"   ❌ {test_case['name']}: Status {response.status_code} (expected 401)")
                all_passed = False

        except Exception as e:
            print(f"   ❌ {test_case['name']}: Error {e}")
            all_passed = False

    return all_passed


def main():
    """Run all authentication tests."""

    print("=" * 70)
    print("EC2 Instance Identity Authentication Test")
    print("=" * 70)
    print("\nThis script tests EC2 instance identity PKCS7 signature")
    print("validation for the monitoring endpoint.\n")

    use_ec2_metadata = '--use-ec2-metadata' in sys.argv

    # Test 1: No authentication
    auth_enforcement_passed = test_without_auth()

    # Test 2: With EC2 identity
    print("\n2. Testing with EC2 instance identity + PKCS7 signature:")
    pkcs7_signature = fetch_ec2_identity(use_ec2_metadata=use_ec2_metadata)

    if pkcs7_signature:
        ec2_validation_passed = test_with_ec2_identity(pkcs7_signature)
    else:
        print("   Note: PKCS7 signatures must be real and cryptographically valid")
        print("   - Use --use-ec2-metadata flag to test on EC2 instance")
        ec2_validation_passed = None  # Not tested

    # Test 3: Missing fields
    fields_validation_passed = test_with_missing_fields()

    # Summary
    print("\n" + "=" * 70)
    print("Test Summary:")
    print("=" * 70)

    status_auth = "✅ PASSED" if auth_enforcement_passed else "❌ FAILED"
    status_fields = "✅ PASSED" if fields_validation_passed else "❌ FAILED"
    status_ec2 = ("✅ PASSED (real signature)" if ec2_validation_passed
                  else ("ℹ️  SKIPPED (use real EC2 metadata)" if ec2_validation_passed is None
                        else "❌ FAILED"))

    print(f"Authentication enforcement: {status_auth}")
    print(f"Missing fields validation:  {status_fields}")
    print(f"EC2 identity validation:    {status_ec2}")

    print("\nTesting on Different Environments:")
    print("  • Local (edge profile):  SPRING_PROFILES_ACTIVE=edge ./gradlew bootRun")
    print("  • Then run:              python3 scripts/test_local_auth.py")
    print("  • EC2 instance:          python3 scripts/test_local_auth.py --use-ec2-metadata")
    print("  • Production:            trigger_fatal_log.py (runs on EC2 instance)")
    print("=" * 70 + "\n")

    # Exit with success if enforcement and fields validation passed
    all_passed = auth_enforcement_passed and fields_validation_passed
    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()
