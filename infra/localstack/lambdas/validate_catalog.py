"""
Step Function state 1: triggered with an S3 object reference, fetches the
uploaded catalog file and validates its shape before anything is loaded.

LOCALSTACK_ENDPOINT, if set, points boto3 at LocalStack instead of real
AWS -- same code runs unchanged in both environments.
"""
import json
import os

import boto3

s3 = boto3.client("s3", endpoint_url=os.environ.get("LOCALSTACK_ENDPOINT"))

REQUIRED_FIELDS = {"productName", "retailer", "basePrice", "deliveryDays", "rating", "inStock"}


def handler(event, context):
    bucket = event["bucket"]
    key = event["key"]

    obj = s3.get_object(Bucket=bucket, Key=key)
    catalog = json.loads(obj["Body"].read())

    if not isinstance(catalog, list) or len(catalog) == 0:
        raise ValueError(f"catalog file {key} must be a non-empty JSON array")

    for i, offer in enumerate(catalog):
        missing = REQUIRED_FIELDS - offer.keys()
        if missing:
            raise ValueError(f"offer at index {i} in {key} is missing fields: {missing}")

    return {"bucket": bucket, "key": key, "offerCount": len(catalog), "catalog": catalog}
