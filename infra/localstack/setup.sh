#!/bin/bash
# Wires up the catalog-ingestion pipeline inside a running LocalStack container:
# S3 bucket -> trigger Lambda -> Step Function (validate -> load into DynamoDB).
set -euo pipefail

ENDPOINT="http://localhost:4566"        # for the CLI, running on the host
LAMBDA_INTERNAL_ENDPOINT="http://localstack:4566"  # for Lambda code, running in a sibling container
REGION="us-east-1"
BUCKET="catalog-uploads"
TABLE_NAME="best-price-engine-catalog"
LAMBDA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/lambdas" && pwd)"
STATE_MACHINE_TEMPLATE="$(dirname "${BASH_SOURCE[0]}")/state-machine.json"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION="$REGION"

aws() { command aws --endpoint-url="$ENDPOINT" "$@"; }

echo "-- creating S3 bucket --"
aws s3 mb "s3://${BUCKET}" || true

echo "-- creating DynamoDB table --"
aws dynamodb create-table \
  --table-name "$TABLE_NAME" \
  --attribute-definitions AttributeName=offerId,AttributeType=S \
  --key-schema AttributeName=offerId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  > /dev/null 2>&1 || echo "  table already exists"

deploy_lambda() {
  local fn="$1" name="${2}" extra_env="${3:-}"
  zip -j -q "/tmp/${fn}.zip" "${LAMBDA_DIR}/${fn}.py"
  local env_vars="Variables={LOCALSTACK_ENDPOINT=${LAMBDA_INTERNAL_ENDPOINT}${extra_env}}"
  aws lambda create-function \
    --function-name "$name" \
    --runtime python3.12 \
    --handler "${fn}.handler" \
    --zip-file "fileb:///tmp/${fn}.zip" \
    --role arn:aws:iam::000000000000:role/lambda-role \
    --timeout 30 \
    --environment "$env_vars" \
    2>/dev/null \
  || aws lambda update-function-code --function-name "$name" --zip-file "fileb:///tmp/${fn}.zip" > /dev/null
  aws lambda wait function-active-v2 --function-name "$name"
  echo "  deployed and ACTIVE: $name"
}

echo "-- deploying validate + load Lambdas --"
deploy_lambda validate_catalog "validate-catalog"
deploy_lambda load_to_dynamodb "load-to-dynamodb" ",CATALOG_TABLE_NAME=${TABLE_NAME}"

VALIDATE_ARN="arn:aws:lambda:${REGION}:000000000000:function:validate-catalog"
LOAD_ARN="arn:aws:lambda:${REGION}:000000000000:function:load-to-dynamodb"

echo "-- creating Step Function state machine --"
sed -e "s|{{VALIDATE_LAMBDA_ARN}}|${VALIDATE_ARN}|" -e "s|{{LOAD_LAMBDA_ARN}}|${LOAD_ARN}|" \
  "$STATE_MACHINE_TEMPLATE" > /tmp/state-machine-resolved.json

aws stepfunctions create-state-machine \
  --name catalog-ingestion-pipeline \
  --definition "file:///tmp/state-machine-resolved.json" \
  --role-arn arn:aws:iam::000000000000:role/stepfunctions-role \
  > /dev/null 2>&1 \
|| aws stepfunctions update-state-machine \
  --state-machine-arn "arn:aws:states:${REGION}:000000000000:stateMachine:catalog-ingestion-pipeline" \
  --definition "file:///tmp/state-machine-resolved.json" > /dev/null

STATE_MACHINE_ARN="arn:aws:states:${REGION}:000000000000:stateMachine:catalog-ingestion-pipeline"

echo "-- deploying trigger Lambda --"
deploy_lambda trigger_pipeline "trigger-pipeline" ",STATE_MACHINE_ARN=${STATE_MACHINE_ARN}"

echo "-- wiring S3 -> trigger-pipeline Lambda notification --"
aws lambda add-permission \
  --function-name trigger-pipeline \
  --statement-id s3invoke \
  --action lambda:InvokeFunction \
  --principal s3.amazonaws.com \
  --source-arn "arn:aws:s3:::${BUCKET}" \
  2>/dev/null || true

aws s3api put-bucket-notification-configuration \
  --bucket "$BUCKET" \
  --notification-configuration "{
    \"LambdaFunctionConfigurations\": [{
      \"LambdaFunctionArn\": \"arn:aws:lambda:${REGION}:000000000000:function:trigger-pipeline\",
      \"Events\": [\"s3:ObjectCreated:*\"]
    }]
  }"

echo "-- setup complete --"
