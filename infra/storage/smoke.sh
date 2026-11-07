#!/bin/sh
set -eu

# Runs inside the storage-init image against an isolated CI stack.
: "${S3_ENDPOINT:?S3_ENDPOINT is required}"
: "${S3_GAME_BUCKET:?S3_GAME_BUCKET is required}"

workdir=$(mktemp -d)
key="storage-smoke/$(cat /proc/sys/kernel/random/uuid).txt"
cleanup() {
    for bucket in "$S3_GAME_BUCKET" langfuse; do
        aws --endpoint-url "$S3_ENDPOINT" s3api delete-object --bucket "$bucket" --key "$key" >/dev/null || true
    done
    rm -rf "$workdir"
}
trap cleanup EXIT

printf 'playly-storage-smoke\n' > "$workdir/input"
for bucket in "$S3_GAME_BUCKET" langfuse; do
    aws --endpoint-url "$S3_ENDPOINT" s3api put-object --bucket "$bucket" --key "$key" --body "$workdir/input"
    url=$(aws --endpoint-url "$S3_ENDPOINT" s3 presign "s3://$bucket/$key" --expires-in 60)
    curl -fsS "$url" > "$workdir/output"
    test "$(cat "$workdir/input")" = "$(cat "$workdir/output")"
    aws --endpoint-url "$S3_ENDPOINT" s3api list-objects-v2 --bucket "$bucket" --prefix "$key" \
        --query 'Contents[0].Key' --output text | grep -Fx "$key"
    aws --endpoint-url "$S3_ENDPOINT" s3api delete-object --bucket "$bucket" --key "$key"
    remaining=$(aws --endpoint-url "$S3_ENDPOINT" s3api list-objects-v2 --bucket "$bucket" --prefix "$key" \
        --query KeyCount --output text)
    test "$remaining" = 0
done
