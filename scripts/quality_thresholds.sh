#!/usr/bin/env bash
set -euo pipefail

# Quality threshold scanner for Kotlin Android code.
# Defaults target production Kotlin sources.

ROOT_DIR="${1:-app/src/main/java}"
FILE_LOC_UI_MAX="${FILE_LOC_UI_MAX:-500}"
FILE_LOC_CORE_MAX="${FILE_LOC_CORE_MAX:-400}"
FUNC_LOC_MAX="${FUNC_LOC_MAX:-40}"

if [[ ! -d "$ROOT_DIR" ]]; then
  echo "ERROR: directory not found: $ROOT_DIR" >&2
  exit 2
fi

if ! command -v rg >/dev/null 2>&1; then
  echo "ERROR: rg is required" >&2
  exit 2
fi

tmp_file_violations="$(mktemp)"
tmp_func_violations="$(mktemp)"
trap 'rm -f "$tmp_file_violations" "$tmp_func_violations"' EXIT

file_total=0
file_violations=0
func_violations=0

while IFS= read -r file; do
  file_total=$((file_total + 1))

  if [[ "$file" == *"/ui/"* || "$file" == *"Screen.kt" || "$file" == *"AppScaffold.kt" ]]; then
    file_limit="$FILE_LOC_UI_MAX"
    file_type="UI"
  else
    file_limit="$FILE_LOC_CORE_MAX"
    file_type="CORE"
  fi

  ncloc=$(awk '
    /^[[:space:]]*$/ { next }
    /^[[:space:]]*\/\// { next }
    /^[[:space:]]*\/\*/ { next }
    /^[[:space:]]*\*/ { next }
    /^[[:space:]]*\*\// { next }
    { c++ }
    END { print c + 0 }
  ' "$file")

  if (( ncloc > file_limit )); then
    file_violations=$((file_violations + 1))
    printf "%s|%s|%s|%s\n" "$file" "$file_type" "$ncloc" "$file_limit" >> "$tmp_file_violations"
  fi

  awk -v max_len="$FUNC_LOC_MAX" -v file="$file" '
    function count_char(str, ch,   i, n, c) {
      n = split(str, c, "")
      cnt = 0
      for (i = 1; i <= n; i++) if (c[i] == ch) cnt++
      return cnt
    }
    function trim(s) { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }
    BEGIN {
      in_fun = 0
      pending = 0
      brace = 0
      start = 0
      name = ""
    }
    {
      line = $0

      if (!in_fun && !pending && line ~ /\<fun[[:space:]]+/) {
        pending = 1
        start = NR
        name = line
        sub(/^.*\<fun[[:space:]]+/, "", name)
        sub(/\(.*/, "", name)
        name = trim(name)
      }

      if (pending && !in_fun) {
        if (index(line, "{") > 0) {
          in_fun = 1
          pending = 0
          brace = count_char(line, "{") - count_char(line, "}")
          if (brace <= 0) {
            len = NR - start + 1
            if (len > max_len) {
              printf "%s|%d|%s|%d|%d\n", file, start, name, len, max_len
            }
            in_fun = 0
            brace = 0
          }
        }
      } else if (in_fun) {
        brace += count_char(line, "{") - count_char(line, "}")
        if (brace <= 0) {
          len = NR - start + 1
          if (len > max_len) {
            printf "%s|%d|%s|%d|%d\n", file, start, name, len, max_len
          }
          in_fun = 0
          brace = 0
        }
      }
    }
  ' "$file" >> "$tmp_func_violations"

done < <(rg --files "$ROOT_DIR" -g '*.kt' | sort)

func_violations=$(wc -l < "$tmp_func_violations" | tr -d ' ')

printf "Quality thresholds\n"
printf "%s\n" "- Scope: $ROOT_DIR"
printf "%s\n" "- File NCLOC max (UI): $FILE_LOC_UI_MAX"
printf "%s\n" "- File NCLOC max (CORE): $FILE_LOC_CORE_MAX"
printf "%s\n\n" "- Function LOC max: $FUNC_LOC_MAX"

printf "Summary\n"
printf "%s\n" "- Kotlin files scanned: $file_total"
printf "%s\n" "- File-size violations: $file_violations"
printf "%s\n\n" "- Function-size violations: $func_violations"

if [[ -s "$tmp_file_violations" ]]; then
  printf "File-size violations\n"
  printf "path|type|ncloc|max\n"
  sort -t'|' -k3,3nr "$tmp_file_violations"
  printf "\n"
fi

if [[ -s "$tmp_func_violations" ]]; then
  printf "Function-size violations\n"
  printf "path|line|function|loc|max\n"
  sort -t'|' -k4,4nr "$tmp_func_violations"
  printf "\n"
fi

if (( file_violations > 0 || func_violations > 0 )); then
  exit 1
fi
