"""
S3 event entry point: fires on every object upload to the catalog bucket,
starts a Step Function execution carrying the bucket/key.

LOCALSTACK_ENDPOINT, if set, points boto3 at LocalStack instead of real
AWS -- same code runs unchanged in both environments.
"""
import json
import os

import boto3

sfn = boto3.client("stepfunctions", endpoint_url=os.environ.get("LOCALSTACK_ENDPOINT"))

STATE_MACHINE_ARN = os.environ["STATE_MACHINE_ARN"]


def handler(event, context):
    for record in event["Records"]:
        bucket = record["s3"]["bucket"]["name"]
        key = record["s3"]["object"]["key"]

        sfn.start_execution(
            stateMachineArn=STATE_MACHINE_ARN,
            input=json.dumps({"bucket": bucket, "key": key}),
        )

    return {"triggered": len(event["Records"])}
