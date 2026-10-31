#!/usr/bin/env bash
set -e

# MoveInSync Mint Dev Token Script
# Usage: ./mint-token.sh [subject] [role1,role2] [tenant1,tenant2] [server_url]

SUBJECT="${1:-dev-admin}"
ROLES="${2:-ADMIN,OPERATOR}"
TENANTS="${3:-acme,globex,*}"
SERVER_URL="${4:-http://localhost:8080}"

ROLES_JSON=$(echo "$ROLES" | awk -F',' '{for(i=1;i<=NF;i++) printf "\"%s\"%s", $i, (i==NF?"":",")}')
TENANTS_JSON=$(echo "$TENANTS" | awk -F',' '{for(i=1;i<=NF;i++) printf "\"%s\"%s", $i, (i==NF?"":",")}')

PAYLOAD="{\"subject\":\"$SUBJECT\",\"roles\":[$ROLES_JSON],\"tenants\":[$TENANTS_JSON]}"

RESPONSE=$(curl -s -X POST "$SERVER_URL/dev/token" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

TOKEN=$(echo "$RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -n "$TOKEN" ]; then
  echo "$TOKEN"
else
  echo "Failed to mint token. Server response: $RESPONSE" >&2
  exit 1
fi
