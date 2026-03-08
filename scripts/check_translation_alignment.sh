#!/usr/bin/env bash
set -euo pipefail

BASE="app/src/main/res/values/strings.xml"
if [ ! -f "$BASE" ]; then
  echo "Base strings file not found: $BASE" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BASE_KEYS="$TMP_DIR/base_keys.txt"
rg -oN '<string name="[^"]+"' "$BASE" | sed -E 's/<string name="([^"]+)"/\1/' | sort -u > "$BASE_KEYS"

CRITICAL_KEYS=(
  error_account_abstraction
  error_api_key_required
  error_insufficient_time_to_sign
  error_invalid_request
  error_no_balance_to_spend
  error_paymaster_not_configured
  error_refund_amount_invalid
  error_relayer_generic
  error_relayer_not_configured
  error_replay_detected
  error_request_expired
  error_status_check
  error_token_not_allowed
  error_transaction_failed
  settings_advanced_hint
  settings_export_requires_wallet
  settings_group_general
  settings_group_network_tokens
  settings_group_payments
  settings_group_privacy_security
  settings_key_management
  settings_key_management_desc
)

python3 - "$BASE" "${CRITICAL_KEYS[@]}" <<'PY' > "$TMP_DIR/en_critical.tsv"
import re
import sys
base = sys.argv[1]
keys = sys.argv[2:]
text = open(base, encoding="utf-8").read()
values = dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', text, re.S))
for k in keys:
    print(f"{k}\t{values.get(k, '').strip()}")
PY

has_error=0
for f in app/src/main/res/values-*/strings.xml; do
  locale_dir="$(basename "$(dirname "$f")")"
  locale_keys="$TMP_DIR/${locale_dir}_keys.txt"
  rg -oN '<string name="[^"]+"' "$f" | sed -E 's/<string name="([^"]+)"/\1/' | sort -u > "$locale_keys"

  missing_count="$(comm -23 "$BASE_KEYS" "$locale_keys" | wc -l | tr -d ' ')"
  extra_count="$(comm -13 "$BASE_KEYS" "$locale_keys" | wc -l | tr -d ' ')"
  if [ "$missing_count" -ne 0 ] || [ "$extra_count" -ne 0 ]; then
    echo "[$locale_dir] key set mismatch: missing=$missing_count extra=$extra_count"
    has_error=1
  fi

  python3 - "$f" "$TMP_DIR/en_critical.tsv" "$locale_dir" <<'PY'
import re
import sys

path = sys.argv[1]
crit_path = sys.argv[2]
locale = sys.argv[3]
text = open(path, encoding="utf-8").read()
values = dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', text, re.S))
en = {}
for line in open(crit_path, encoding="utf-8"):
    k, v = line.rstrip("\n").split("\t", 1)
    en[k] = v

# "General" can be valid in many languages and matches English exactly.
allowed_identical = {"settings_group_general"}

for k, en_val in en.items():
    local_val = values.get(k, "").strip()
    if not local_val:
        print(f"[{locale}] missing critical key: {k}")
        continue
    if local_val == en_val and k not in allowed_identical and locale != "values":
        print(f"[{locale}] english critical value detected: {k}")
PY
done | tee "$TMP_DIR/critical_report.txt"

if [ -s "$TMP_DIR/critical_report.txt" ]; then
  has_error=1
fi

if [ "$has_error" -ne 0 ]; then
  echo "Translation alignment check: FAILED" >&2
  exit 1
fi

echo "Translation alignment check: OK"
