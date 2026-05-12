#!/bin/bash
# Runs automatically when LocalStack reaches "ready" state.
# Creates the SNS topic, SQS queues (main + DLQ), the subscription that wires
# them together, and DynamoDB tables for the future analytics service.

set -e

echo "==> Initializing AWS resources in LocalStack..."

REGION="us-east-1"

# ---- SNS topic: payment lifecycle events ----
echo "Creating SNS topic: payment-events"
awslocal sns create-topic --name payment-events --region $REGION

PAYMENT_EVENTS_ARN="arn:aws:sns:us-east-1:000000000000:payment-events"

# ---- SQS queues: analytics + dead letter queue ----
echo "Creating SQS DLQ: payment-analytics-dlq"
awslocal sqs create-queue --queue-name payment-analytics-dlq --region $REGION

DLQ_ARN="arn:aws:sqs:us-east-1:000000000000:payment-analytics-dlq"

echo "Creating SQS queue: payment-analytics with DLQ redrive policy"
awslocal sqs create-queue \
  --queue-name payment-analytics \
  --attributes "{
    \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\",
    \"VisibilityTimeout\": \"30\",
    \"MessageRetentionPeriod\": \"345600\"
  }" \
  --region $REGION

ANALYTICS_QUEUE_ARN="arn:aws:sqs:us-east-1:000000000000:payment-analytics"
ANALYTICS_QUEUE_URL="http://localhost:4566/000000000000/payment-analytics"

# ---- Subscribe SQS to SNS ----
echo "Subscribing payment-analytics queue to payment-events topic"
awslocal sns subscribe \
  --topic-arn $PAYMENT_EVENTS_ARN \
  --protocol sqs \
  --notification-endpoint $ANALYTICS_QUEUE_ARN \
  --attributes RawMessageDelivery=true \
  --region $REGION

# Grant SNS permission to publish to SQS
awslocal sqs set-queue-attributes \
  --queue-url $ANALYTICS_QUEUE_URL \
  --attributes "{
    \"Policy\": \"{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Principal\\\":{\\\"AWS\\\":\\\"*\\\"},\\\"Action\\\":\\\"sqs:SendMessage\\\",\\\"Resource\\\":\\\"${ANALYTICS_QUEUE_ARN}\\\",\\\"Condition\\\":{\\\"ArnEquals\\\":{\\\"aws:SourceArn\\\":\\\"${PAYMENT_EVENTS_ARN}\\\"}}}]}\"
  }" \
  --region $REGION

# ---- DynamoDB tables (for Project 2 later) ----
echo "Creating DynamoDB table: account-analytics"
awslocal dynamodb create-table \
  --table-name account-analytics \
  --attribute-definitions \
      AttributeName=accountId,AttributeType=S \
      AttributeName=window,AttributeType=S \
  --key-schema \
      AttributeName=accountId,KeyType=HASH \
      AttributeName=window,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --region $REGION

echo "==> Done. AWS resources ready:"
echo "    SNS topic:        payment-events"
echo "    SQS queue:        payment-analytics  (DLQ: payment-analytics-dlq, max receives: 3)"
echo "    DynamoDB table:   account-analytics"
