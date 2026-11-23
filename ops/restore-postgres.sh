#!/usr/bin/env bash
set -euo pipefail

: "${BACKUP_OBJECT:?Set BACKUP_OBJECT to s3://bucket/file.dump}"
: "${RESTORE_DATABASE_URL:?Set RESTORE_DATABASE_URL}"
: "${RESTORE_CONFIRM:?Set RESTORE_CONFIRM=RESTORE_NON_PRODUCTION_DATABASE}"

if [[ "$RESTORE_CONFIRM" != "RESTORE_NON_PRODUCTION_DATABASE" ]]; then
  echo "Refusing restore: confirmation is missing or invalid" >&2
  exit 1
fi

archive="$(mktemp --suffix=.dump)"
trap 'rm -f "$archive"' EXIT

aws s3 cp "$BACKUP_OBJECT" "$archive" --only-show-errors

pg_restore \
  --clean \
  --if-exists \
  --no-owner \
  --no-acl \
  --dbname="$RESTORE_DATABASE_URL" \
  "$archive"

echo "Restore completed."