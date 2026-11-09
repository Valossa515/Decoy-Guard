#!/usr/bin/env bash
# Checks that a PR title follows the commit convention in CONTRIBUTING.md.
# The title becomes the squash commit on development.
# Input: TITLE, and AUTHOR (Dependabot titles have their own format).
set -euo pipefail

types='feat|fix|test|refactor|docs|ci|build|chore'
scopes='core|starter|demo|spec|ci|docs|deps|release'
pattern="^(${types})(\((${scopes})\))?!?: [a-z0-9].*[^.]$"

# Dependabot writes its own titles: "build(deps): Bump x from 1 to 2" or
# "ci(deps-dev): Bump ...". The prefix comes from .github/dependabot.yml.
dependabot_pattern='^(build|ci)\(deps(-dev)?\): [Bb]ump .+$'
if [[ "${AUTHOR:-}" == "dependabot[bot]" && "${TITLE}" =~ ${dependabot_pattern} ]]; then
  echo "PR title OK (Dependabot): ${TITLE}"
  exit 0
fi

if [[ ! "${TITLE}" =~ ${pattern} ]]; then
  echo "PR title does not follow the convention: ${TITLE}"
  echo "Expected: <type>(<scope>): <summary>, type in {${types//|/, }}, optional scope in {${scopes//|/, }}."
  echo "The summary starts in lowercase and has no final period. See CONTRIBUTING.md."
  exit 1
fi

summary="${TITLE#*: }"
if [[ ${#summary} -gt 72 ]]; then
  echo "PR title summary has ${#summary} characters, the limit is 72: ${summary}"
  exit 1
fi

echo "PR title OK: ${TITLE}"
