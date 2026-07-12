"""
Step Function state 2: takes the validated catalog and writes each offer
as an item into a DynamoDB table -- a genuinely AWS-native persistence
step, not a call back into any locally-running service (which real AWS
Lambda could never reach anyway).

LOCALSTACK_ENDPOINT, if set, points boto3 at LocalStack instead of real
AWS -- same code runs unchanged in both environments.
"""
import os
import time
import uuid

import boto3

dynamodb = boto3.resource("dynamodb", endpoint_url=os.environ.get("LOCALSTACK_ENDPOINT"))
TABLE_NAME = os.environ["CATALOG_TABLE_NAME"]


def handler(event, context):
    table = dynamodb.Table(TABLE_NAME)
    catalog = event["catalog"]
    loaded_at = int(time.time())

    for offer in catalog:
        table.put_item(Item={
            "offerId": str(uuid.uuid4()),
            "productName": offer["productName"],
            "retailer": offer["retailer"],
            "basePrice": str(offer["basePrice"]),
            "deliveryDays": offer["deliveryDays"],
            "rating": str(offer["rating"]),
            "inStock": offer["inStock"],
            "availableQuantity": offer.get("availableQuantity", 0),
            "sourceBucket": event["bucket"],
            "sourceKey": event["key"],
            "loadedAt": loaded_at,
        })

    return {"bucket": event["bucket"], "key": event["key"], "offersLoaded": len(catalog)}
