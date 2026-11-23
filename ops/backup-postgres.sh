#!/usr/bin/env bash
set -euo pipefail

: "${PGHOST:?Set PGHOST}"
: "${PGDATABASE:?Set PGDATABASE}"
: "${PGUSER:?Set PGUSER}"
: "${PGSSLMODE:?Set PGSSLMODE=verify-full}"
: "${PGSSLROOTCERT:?Set PGSSLROOTCERT}"
: "${S3_BACKUP_BUCKET:?Set S3_BACKUP_BUCKET}"

if [[ "$PGSSLMODE" != "verify-full" ]]; then
  echo "Refusing backup: PGSSLMODE must be verify-full" >&2
  exit 1
fi

backup_prefix="${S3_BACKUP_PREFIX:-postgres}"
timestamp="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
archive="$(mktemp --suffix=.dump)"

trap 'rm -f "$archive"' EXIT

pg_dump \
  --format=custom \
  --compress=9 \
  --no-owner \
  --no-acl \
  --file="$archive"

object_key="${backup_prefix}/${timestamp}.dump"

aws s3 cp \
  "$archive" \
  "s3://${S3_BACKUP_BUCKET}/${object_key}" \
  --only-show-errors

echo "Backup uploaded: s3://${S3_BACKUP_BUCKET}/${object_key}"