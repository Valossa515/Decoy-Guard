#!/usr/bin/env bash
# Checks that a PR title follows the commit convention in CONTRIBUTING.md.
# The title becomes the squash commit on development.
# Input: TITLE, and AUTHOR (Dependabot titles may be longer than 72 characters).
set -euo pipefail

types='feat|fix|test|refactor|docs|ci|build|chore'
scopes='core|starter|demo|spec|ci|docs|deps|release'
pattern="^(${types})(\((${scopes})\))?!?: [a-z0-9].*[^.]$"

if [[ ! "${TITLE}" =~ ${pattern} ]]; then
  echo "PR title does not follow the convention: ${TITLE}"
  echo "Expected: <type>(<scope>): <summary>, type in {${types//|/, }}, optional scope in {${scopes//|/, }}."
  echo "The summary starts in lowercase and has no final period. See CONTRIBUTING.md."
  exit 1
fi

summary="${TITLE#*: }"
if [[ "${AUTHOR:-}" != "dependabot[bot]" && ${#summary} -gt 72 ]]; then
  echo "PR title summary has ${#summary} characters, the limit is 72: ${summary}"
  exit 1
fi

echo "PR title OK: ${TITLE}"
