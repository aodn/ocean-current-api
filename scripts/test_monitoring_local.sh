#!/bin/bash

# Local testing script for monitoring endpoint
# This tests the endpoint without AWS IAM authentication (development mode)

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENDPOINT="${MONITORING_ENDPOINT:-http://localhost:8080/api/v1/monitoring/fatal-log}"

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}Testing Monitoring Endpoint (Local)${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""
echo -e "Endpoint: ${GREEN}${ENDPOINT}${NC}"
echo ""

# Make the request
echo -e "${BLUE}Sending POST request...${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json")

# Extract status code and body
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo ""
echo -e "${BLUE}Response Status:${NC} ${HTTP_CODE}"
echo -e "${BLUE}Response Body:${NC}"
echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
echo ""

# Check if successful
if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✅ SUCCESS! Monitoring endpoint is working correctly.${NC}"
    exit 0
else
    echo -e "${RED}❌ FAILED! Expected status 200, got ${HTTP_CODE}${NC}"
    exit 1
fi
