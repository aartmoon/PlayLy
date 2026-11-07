#!/bin/sh
set -eu

: "${S3_ENDPOINT:?S3_ENDPOINT is required}"
: "${S3_GAME_BUCKET:?S3_GAME_BUCKET is required}"

for bucket in "$S3_GAME_BUCKET" langfuse; do
    if ! aws --endpoint-url "$S3_ENDPOINT" s3api head-bucket --bucket "$bucket" 2>/dev/null; then
        aws --endpoint-url "$S3_ENDPOINT" s3api create-bucket --bucket "$bucket"
    fi
    aws --endpoint-url "$S3_ENDPOINT" s3api head-bucket --bucket "$bucket"
done
