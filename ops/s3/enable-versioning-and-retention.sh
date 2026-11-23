#!/usr/bin/env bash
set -euo pipefail

: "${S3_MEDIA_BUCKET:?Set S3_MEDIA_BUCKET}"
: "${S3_BACKUP_BUCKET:?Set S3_BACKUP_BUCKET}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

aws s3api put-bucket-versioning \
  --bucket "$S3_MEDIA_BUCKET" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-lifecycle-configuration \
  --bucket "$S3_MEDIA_BUCKET" \
  --lifecycle-configuration "file://${repo_root}/ops/s3/media-lifecycle-5-days.json"

aws s3api put-bucket-versioning \
  --bucket "$S3_BACKUP_BUCKET" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-lifecycle-configuration \
  --bucket "$S3_BACKUP_BUCKET" \
  --lifecycle-configuration "file://${repo_root}/ops/s3/database-backup-lifecycle-5-days.json"

echo "Enabled versioning and five-day retention."