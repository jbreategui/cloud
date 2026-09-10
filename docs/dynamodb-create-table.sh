#!/bin/bash
# Crea la tabla DynamoDB de likes. Se corre una sola vez a mano contra la cuenta de AWS,
# antes de usar el endpoint POST /api/images/{filename}/like.

aws dynamodb create-table \
  --table-name image_likes \
  --attribute-definitions AttributeName=object_key,AttributeType=S \
  --key-schema AttributeName=object_key,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-2
