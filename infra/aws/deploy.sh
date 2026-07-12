#!/bin/bash
# Deploys the catalog-ingestion pipeline to real AWS: S3 bucket -> trigger
# Lambda -> Step Function (validate -> load into DynamoDB). Mirrors
# infra/localstack/setup.sh exactly, minus --endpoint-url and using the
# real IAM roles created for this project.
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-eu-west-2}"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
BUCKET="best-price-engine-catalog-uploads-${ACCOUNT_ID}"
TABLE_NAME="best-price-engine-catalog"
LAMBDA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../localstack/lambdas" && pwd)"
STATE_MACHINE_TEMPLATE="$(dirname "${BASH_SOURCE[0]}")/../localstack/state-machine.json"
LAMBDA_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/best-price-engine-lambda-role"
STATES_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/best-price-engine-states-role"

echo "-- account: ${ACCOUNT_ID}, region: ${REGION}, bucket: ${BUCKET} --"

echo "-- creating S3 bucket --"
aws s3 mb "s3://${BUCKET}" --region "$REGION" 2>&1 | grep -v "BucketAlreadyOwnedByYou" || true

echo "-- creating DynamoDB table --"
aws dynamodb create-table \
  --table-name "$TABLE_NAME" \
  --attribute-definitions AttributeName=offerId,AttributeType=S \
  --key-schema AttributeName=offerId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region "$REGION" \
  > /dev/null 2>&1 || echo "  table already exists"

deploy_lambda() {
  local fn="$1" name="best-price-engine-${2}" extra_env="${3:-}"
  zip -j -q "/tmp/${fn}.zip" "${LAMBDA_DIR}/${fn}.py"
  local env_vars="Variables={${extra_env#,}}"
  aws lambda create-function \
    --function-name "$name" \
    --runtime python3.12 \
    --handler "${fn}.handler" \
    --zip-file "fileb:///tmp/${fn}.zip" \
    --role "$LAMBDA_ROLE_ARN" \
    --timeout 30 \
    --environment "$env_vars" \
    --region "$REGION" \
    2>&1 | grep -v "^{" \
  || aws lambda update-function-code --function-name "$name" --zip-file "fileb:///tmp/${fn}.zip" --region "$REGION" > /dev/null
  aws lambda wait function-active-v2 --function-name "$name" --region "$REGION"
  echo "  deployed and ACTIVE: $name"
}

echo "-- deploying validate + load Lambdas --"
deploy_lambda validate_catalog "validate-catalog"
deploy_lambda load_to_dynamodb "load-to-dynamodb" ",CATALOG_TABLE_NAME=${TABLE_NAME}"

VALIDATE_ARN="arn:aws:lambda:${REGION}:${ACCOUNT_ID}:function:best-price-engine-validate-catalog"
LOAD_ARN="arn:aws:lambda:${REGION}:${ACCOUNT_ID}:function:best-price-engine-load-to-dynamodb"

echo "-- creating Step Function state machine --"
sed -e "s|{{VALIDATE_LAMBDA_ARN}}|${VALIDATE_ARN}|" -e "s|{{LOAD_LAMBDA_ARN}}|${LOAD_ARN}|" \
  "$STATE_MACHINE_TEMPLATE" > /tmp/state-machine-resolved-aws.json

STATE_MACHINE_ARN="arn:aws:states:${REGION}:${ACCOUNT_ID}:stateMachine:best-price-engine-catalog-pipeline"

aws stepfunctions create-state-machine \
  --name best-price-engine-catalog-pipeline \
  --definition "file:///tmp/state-machine-resolved-aws.json" \
  --role-arn "$STATES_ROLE_ARN" \
  --region "$REGION" \
  > /dev/null 2>&1 \
|| aws stepfunctions update-state-machine \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --definition "file:///tmp/state-machine-resolved-aws.json" \
  --region "$REGION" > /dev/null

echo "-- deploying trigger Lambda --"
deploy_lambda trigger_pipeline "trigger-pipeline" ",STATE_MACHINE_ARN=${STATE_MACHINE_ARN}"

echo "-- wiring S3 -> trigger-pipeline Lambda notification --"
aws lambda add-permission \
  --function-name best-price-engine-trigger-pipeline \
  --statement-id s3invoke \
  --action lambda:InvokeFunction \
  --principal s3.amazonaws.com \
  --source-arn "arn:aws:s3:::${BUCKET}" \
  --region "$REGION" \
  2>&1 | grep -v "^{" || true

aws s3api put-bucket-notification-configuration \
  --bucket "$BUCKET" \
  --notification-configuration "{
    \"LambdaFunctionConfigurations\": [{
      \"LambdaFunctionArn\": \"arn:aws:lambda:${REGION}:${ACCOUNT_ID}:function:best-price-engine-trigger-pipeline\",
      \"Events\": [\"s3:ObjectCreated:*\"]
    }]
  }" \
  --region "$REGION"

echo "-- deploy complete: bucket=${BUCKET} --"
