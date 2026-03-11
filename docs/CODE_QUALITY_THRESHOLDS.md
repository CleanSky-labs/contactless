# Code Quality Thresholds (Kotlin/Android)

This project uses practical thresholds aligned with common Android/Kotlin quality expectations.

## Threshold rules

- File size (`NCLOC`, non-comment/non-blank lines):
  - UI files (`/ui/`, `*Screen.kt`, `AppScaffold.kt`): max `500`
  - Core files (other production Kotlin): max `400`
- Function size (`LOC`, physical lines from `fun` start to closing brace): max `40`

Notes:
- These thresholds are intentionally pragmatic. They are not language-level hard limits.
- Cyclomatic complexity and coverage gates remain enforced by Detekt/JaCoCo Gradle tasks.

## Analyzer script

Script: `scripts/quality_thresholds.sh`

Default scope:
- `app/src/main/java`

Usage examples:

```bash
# Default run (production code)
./scripts/quality_thresholds.sh

# Scan tests too
./scripts/quality_thresholds.sh app/src/test/java

# Override thresholds
FILE_LOC_UI_MAX=450 FILE_LOC_CORE_MAX=350 FUNC_LOC_MAX=35 ./scripts/quality_thresholds.sh
```

Output format:
- Summary counts
- File violations table: `path|type|ncloc|max`
- Function violations table: `path|line|function|loc|max`

Exit code:
- `0` if no violations
- `1` if violations exist
- `2` for usage/tooling errors
