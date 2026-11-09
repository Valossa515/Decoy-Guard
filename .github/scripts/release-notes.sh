#!/usr/bin/env bash
# Prints the release notes for VERSION in Markdown: the commits since the
# previous v* tag, grouped by Conventional Commits type. Squash commits on
# development carry the PR title, so merge commits are skipped.
# Input: VERSION (X.Y.Z). Run from a full clone (fetch-depth 0).
set -euo pipefail

previous="$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
if [[ -n "${previous}" ]]; then
  range="${previous}..HEAD"
else
  range="HEAD"
fi

subjects="$(git log --no-merges --reverse --format='%s' "${range}")"

section() {
  local title="$1" pattern="$2" lines
  lines="$(grep -E "${pattern}" <<<"${subjects}" || true)"
  if [[ -n "${lines}" ]]; then
    echo "## ${title}"
    echo
    sed -E 's/^[a-z]+(\([a-z-]+\))?!?: //; s/^/* /' <<<"${lines}"
    echo
  fi
}

known='^(feat|fix|refactor|test|docs|build|ci|chore)(\([a-z-]+\))?!?: '

section "Breaking changes" '^[a-z]+(\([a-z-]+\))?!: '
section "Features" '^feat(\([a-z-]+\))?!?: '
section "Fixes" '^fix(\([a-z-]+\))?!?: '
section "Refactoring" '^refactor(\([a-z-]+\))?!?: '
section "Tests" '^test(\([a-z-]+\))?!?: '
section "Documentation" '^docs(\([a-z-]+\))?!?: '
section "Build and dependencies" '^build(\([a-z-]+\))?!?: '
section "CI" '^ci(\([a-z-]+\))?!?: '
section "Chores" '^chore(\([a-z-]+\))?!?: '

other="$(grep -Ev "${known}" <<<"${subjects}" || true)"
if [[ -n "${other}" ]]; then
  echo "## Other"
  echo
  sed 's/^/* /' <<<"${other}"
  echo
fi

if [[ -n "${previous}" ]]; then
  echo "Changes since ${previous}."
else
  echo "First release."
fi
