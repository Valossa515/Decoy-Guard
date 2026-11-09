#!/usr/bin/env bash
# validate-spec.sh — Validates TOON specification files against the SDD schemas
#
# Checks .spec.toon, .acceptance.toon and .contract.toon files for the
# structural rules defined in specs/*.schema.md: required blocks and fields,
# ID formats (FT-NNN, BR-NNN, AC-NNN), unique IDs, declared row counts,
# allowed enum values, and cross-file feature references.
#
# Usage:
#   bash validate-spec.sh <file-or-directory> [...]
#
# Examples:
#   bash validate-spec.sh specs/examples
#   bash validate-spec.sh .agent/specs/features/create-order.spec.toon

set -euo pipefail

[ "$#" -ge 1 ] || { echo "Usage: $0 <file-or-directory> [...]"; exit 2; }

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RESET='\033[0m'

ERRORS=0
FILES_CHECKED=0
KNOWN_FEATURE_IDS=""

fail() {
  # fail <file> <message>
  echo -e "  ${RED}✘${RESET} $1 — $2"
  ERRORS=$((ERRORS + 1))
}

warn() {
  echo -e "  ${YELLOW}⚠${RESET}  $1 — $2"
}

# Print the rows of every tabular array named <tbl> (columns still comma-joined).
table_rows() {
  # table_rows <file> <table-name>
  awk -v tbl="$2" '
    function ind(s) { match(s, /[^ ]/); return RSTART - 1 }
    inTable {
      if ($0 ~ /^[ ]*$/ || $0 ~ /^[ ]*#/ || ind($0) <= tIndent) { inTable = 0 }
      else { row = $0; sub(/^[ ]*/, "", row); print row; next }
    }
    $0 ~ ("^[ ]*" tbl "\\[[0-9]+\\](\\{[^}]*\\})?:[ ]*$") {
      inTable = 1; tIndent = ind($0)
    }
  ' "$1"
}

# Every "name[N]{...}:" or "name[N]:" header must be followed by exactly N rows.
check_row_counts() {
  local file="$1" rel="$2"
  local mismatches
  mismatches=$(awk '
    function ind(s) { match(s, /[^ ]/); return RSTART - 1 }
    function flush() {
      if (inTable && rows != expected)
        printf "line %d: %s declares [%d] but has %d row(s)\n", tLine, tName, expected, rows
      inTable = 0
    }
    {
      if (inTable) {
        if ($0 ~ /^[ ]*$/ || $0 ~ /^[ ]*#/ || ind($0) <= tIndent) flush()
        else { rows++; next }
      }
      if ($0 ~ /^[ ]*[A-Za-z0-9_-]+\[[0-9]+\](\{[^}]*\})?:[ ]*$/) {
        tIndent = ind($0); tLine = NR; rows = 0
        tName = $0; sub(/^[ ]*/, "", tName); sub(/\[.*/, "", tName)
        expected = $0; sub(/^[^[]*\[/, "", expected); sub(/\].*/, "", expected)
        expected = expected + 0
        inTable = 1
      }
    }
    END { flush() }
  ' "$file")

  if [ -n "$mismatches" ]; then
    while IFS= read -r m; do fail "$rel" "$m"; done <<< "$mismatches"
  fi
}

# check_ids <file> <rel> <table> <column-index> <pattern> <label>
# Validates format and uniqueness of an ID column in a tabular array.
check_ids() {
  local file="$1" rel="$2" tbl="$3" col="$4" pattern="$5" label="$6"
  local ids id
  ids=$(table_rows "$file" "$tbl" | cut -d',' -f"$col")
  [ -n "$ids" ] || return 0

  while IFS= read -r id; do
    if ! echo "$id" | grep -qE "$pattern"; then
      fail "$rel" "$tbl: invalid $label id '$id' (expected pattern: $pattern)"
    fi
  done <<< "$ids"

  local dupes
  dupes=$(echo "$ids" | sort | uniq -d)
  if [ -n "$dupes" ]; then
    while IFS= read -r id; do
      fail "$rel" "$tbl: duplicate $label id '$id'"
    done <<< "$dupes"
  fi
}

require_line() {
  # require_line <file> <rel> <regex> <message>
  grep -qE "$3" "$1" || fail "$2" "$4"
}

get_feature_ref() {
  grep -m1 -E '^[ ]+feature-ref:' "$1" | sed 's/.*feature-ref:[ ]*//' | tr -d ' '
}

check_feature_ref() {
  local file="$1" rel="$2" ref
  ref=$(get_feature_ref "$file")
  if [ -z "$ref" ]; then
    fail "$rel" "missing 'feature-ref:' field"
    return
  fi
  if ! echo "$ref" | grep -qE '^FT-[0-9]+$'; then
    fail "$rel" "feature-ref '$ref' is not a valid feature id (FT-NNN)"
    return
  fi
  # Cross-file check: only when we discovered feature specs alongside this file
  if [ -n "$KNOWN_FEATURE_IDS" ] && ! echo "$KNOWN_FEATURE_IDS" | grep -qx "$ref"; then
    fail "$rel" "feature-ref '$ref' does not match any .spec.toon feature id found ($(echo "$KNOWN_FEATURE_IDS" | tr '\n' ' ' | sed 's/ $//'))"
  fi
}

validate_feature() {
  local file="$1" rel="$2"

  require_line "$file" "$rel" '^feature:' "missing 'feature:' block"
  require_line "$file" "$rel" '^[ ]+id:[ ]*FT-[0-9]+[ ]*$' "missing or invalid 'feature.id' (expected FT-NNN)"
  require_line "$file" "$rel" '^[ ]+name:[ ]*[^ ]' "missing 'feature.name'"
  require_line "$file" "$rel" '^requirement:' "missing 'requirement:' block"
  require_line "$file" "$rel" '^[ ]+summary:[ ]*[^ ]' "missing 'requirement.summary'"
  require_line "$file" "$rel" '^inputs\[[0-9]+\]\{' "missing 'inputs' tabular array"
  require_line "$file" "$rel" '^outputs:' "missing 'outputs:' block"
  require_line "$file" "$rel" '^[ ]+success:' "missing 'outputs.success'"
  require_line "$file" "$rel" '^[ ]+errors\[[0-9]+\]\{' "missing 'outputs.errors' tabular array"
  require_line "$file" "$rel" '^business-rules\[[0-9]+\]\{' "missing 'business-rules' tabular array"

  # inputs must declare name, type and required columns
  local inputs_header
  inputs_header=$(grep -m1 -E '^inputs\[[0-9]+\]\{' "$file" || true)
  if [ -n "$inputs_header" ]; then
    for col in name type required; do
      echo "$inputs_header" | grep -qE "[{,]${col}[},]" || \
        fail "$rel" "inputs: missing required column '$col'"
    done
  fi

  # errors rows must have code, status and when (3 non-empty columns)
  local row
  while IFS= read -r row; do
    [ -n "$row" ] || continue
    local code status when
    code=$(echo "$row" | cut -d',' -f1)
    status=$(echo "$row" | cut -d',' -f2)
    when=$(echo "$row" | cut -d',' -f3)
    if [ -z "$code" ] || [ -z "$status" ] || [ -z "$when" ]; then
      fail "$rel" "errors: row '$row' must have code, status and when"
    fi
    echo "$status" | grep -qE '^[0-9]{3}$' || \
      fail "$rel" "errors: '$code' has invalid HTTP status '$status'"
  done < <(table_rows "$file" "errors")

  check_ids "$file" "$rel" "business-rules" 1 '^BR-[0-9]+$' "business rule"

  # If no assumptions block, remind (schema says assumptions must be explicit)
  grep -qE '^assumptions\[[0-9]+\]' "$file" || \
    warn "$rel" "no 'assumptions' block (schema recommends flagging assumptions explicitly)"
}

validate_acceptance() {
  local file="$1" rel="$2"

  require_line "$file" "$rel" '^acceptance:' "missing 'acceptance:' block"
  check_feature_ref "$file" "$rel"
  require_line "$file" "$rel" '^criteria\[[0-9]+\]\{' "missing 'criteria' tabular array"

  local header
  header=$(grep -m1 -E '^criteria\[[0-9]+\]\{' "$file" || true)
  if [ -n "$header" ]; then
    for col in id scenario given when "then" priority; do
      echo "$header" | grep -qE "[{,]${col}[},]" || \
        fail "$rel" "criteria: missing required column '$col'"
    done
  fi

  check_ids "$file" "$rel" "criteria" 1 '^AC-[0-9]+$' "criterion"

  # priority must be a known value; business-rule-ref (col 7) is optional
  local row
  while IFS= read -r row; do
    [ -n "$row" ] || continue
    local id priority br_ref
    id=$(echo "$row" | cut -d',' -f1)
    priority=$(echo "$row" | cut -d',' -f6)
    br_ref=$(echo "$row" | cut -d',' -f7)
    echo "$priority" | grep -qE '^(must-have|should-have|nice-to-have)$' || \
      fail "$rel" "criteria: '$id' has invalid priority '$priority' (must-have | should-have | nice-to-have)"
    if [ -n "$br_ref" ] && ! echo "$br_ref" | grep -qE '^BR-[0-9]+$'; then
      fail "$rel" "criteria: '$id' has invalid business-rule-ref '$br_ref' (expected BR-NNN)"
    fi
  done < <(table_rows "$file" "criteria")
}

validate_contract() {
  local file="$1" rel="$2"

  require_line "$file" "$rel" '^contract:' "missing 'contract:' block"
  check_feature_ref "$file" "$rel"
  require_line "$file" "$rel" '^layers:' "missing 'layers:' block"
  require_line "$file" "$rel" 'files\[[0-9]+\]\{' "layers must declare at least one 'files' tabular array"
  require_line "$file" "$rel" '^migrations\[[0-9]+\]' "missing 'migrations' array (declare [0] if none)"
  require_line "$file" "$rel" '^dependencies:' "missing 'dependencies:' block"
  require_line "$file" "$rel" '^[ ]+uses\[[0-9]+\]' "missing 'dependencies.uses' (declare [0] if none)"
  require_line "$file" "$rel" '^[ ]+introduces\[[0-9]+\]' "missing 'dependencies.introduces' (declare [0] if none)"
  require_line "$file" "$rel" '^test-strategy:' "missing 'test-strategy:' block"
  require_line "$file" "$rel" '^[ ]+unit-tests:' "missing 'test-strategy.unit-tests'"
  require_line "$file" "$rel" '^[ ]+integration-tests:' "missing 'test-strategy.integration-tests'"
  require_line "$file" "$rel" '^[ ]+excluded\[[0-9]+\]' "missing 'test-strategy.excluded' (declare [0] if none)"

  # every file row needs action create|modify; paths must not repeat across layers
  local row paths=""
  while IFS= read -r row; do
    [ -n "$row" ] || continue
    local path action
    path=$(echo "$row" | cut -d',' -f1)
    action=$(echo "$row" | cut -d',' -f2)
    echo "$action" | grep -qE '^(create|modify)$' || \
      fail "$rel" "layers: file '$path' has invalid action '$action' (create | modify)"
    paths="$paths$path"$'\n'
  done < <(table_rows "$file" "files")

  local dupes
  dupes=$(printf '%s' "$paths" | sort | uniq -d)
  if [ -n "$dupes" ]; then
    while IFS= read -r row; do
      fail "$rel" "layers: file '$row' appears in more than one layer"
    done <<< "$dupes"
  fi

  # excluded rows must carry a reason
  while IFS= read -r row; do
    [ -n "$row" ] || continue
    local target reason
    target=$(echo "$row" | cut -d',' -f1)
    reason=$(echo "$row" | cut -d',' -f2)
    [ -n "$reason" ] || fail "$rel" "test-strategy.excluded: '$target' has no reason"
  done < <(table_rows "$file" "excluded")
}

validate_file() {
  local file="$1"
  local rel="$file"
  local before=$ERRORS

  if [ ! -s "$file" ]; then
    fail "$rel" "file is empty"
    return
  fi

  FILES_CHECKED=$((FILES_CHECKED + 1))
  check_row_counts "$file" "$rel"

  case "$file" in
    *.spec.toon)       validate_feature "$file" "$rel" ;;
    *.acceptance.toon) validate_acceptance "$file" "$rel" ;;
    *.contract.toon)   validate_contract "$file" "$rel" ;;
    *) warn "$rel" "unknown spec type (expected .spec.toon, .acceptance.toon or .contract.toon)" ;;
  esac

  if [ "$ERRORS" -eq "$before" ]; then
    echo -e "  ${GREEN}✔${RESET} $rel"
  fi
}

# ─── Collect files ────────────────────────────────────────────────────────────
FILES=()
for arg in "$@"; do
  if [ -d "$arg" ]; then
    while IFS= read -r -d '' f; do FILES+=("$f"); done \
      < <(find "$arg" -name "*.toon" -print0 | sort -z)
  elif [ -f "$arg" ]; then
    FILES+=("$arg")
  else
    echo -e "${RED}✘ Not found: $arg${RESET}"
    exit 2
  fi
done

if [ "${#FILES[@]}" -eq 0 ]; then
  echo -e "${YELLOW}No .toon spec files found — nothing to validate.${RESET}"
  exit 0
fi

# Feature ids discovered across all inputs enable cross-file feature-ref checks
KNOWN_FEATURE_IDS=$(for f in "${FILES[@]}"; do
  case "$f" in
    *.spec.toon) grep -hE '^[ ]+id:[ ]*FT-[0-9]+' "$f" 2>/dev/null | sed 's/.*id:[ ]*//' | tr -d ' ' ;;
  esac
done | sort -u)

echo ""
echo "Validating TOON specs..."
echo "───────────────────────────────────────"
for f in "${FILES[@]}"; do
  validate_file "$f"
done
echo "───────────────────────────────────────"
echo "Files checked: $FILES_CHECKED"

if [ "$ERRORS" -gt 0 ]; then
  echo -e "${RED}Errors: $ERRORS — fix the specs above before proceeding.${RESET}"
  exit 1
fi

echo -e "${GREEN}All specs valid!${RESET}"
