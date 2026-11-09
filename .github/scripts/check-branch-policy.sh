#!/usr/bin/env bash
# Checks the source branch name and the merge target of a PR (CONTRIBUTING.md).
# Input: HEAD_REF (source branch) and BASE_REF (target branch).
set -euo pipefail

slug='[a-z0-9][a-z0-9-]*'
work_branch="^(feature/ft-[0-9]{3}-${slug}|(fix|refactor|test|docs|chore|ci)/${slug}|claude/${slug}|dependabot/.+)$"

fail() {
  echo "$1"
  echo "See the branch model in CONTRIBUTING.md."
  exit 1
}

case "${BASE_REF}" in
  main)
    if [[ "${HEAD_REF}" == "development" || "${HEAD_REF}" =~ ^hotfix/${slug}$ ]]; then
      echo "OK: ${HEAD_REF} -> main"
      exit 0
    fi
    fail "Only development (release) or hotfix/* may target main, got ${HEAD_REF}."
    ;;
  development)
    if [[ "${HEAD_REF}" == "main" || "${HEAD_REF}" =~ ${work_branch} ]]; then
      echo "OK: ${HEAD_REF} -> development"
      exit 0
    fi
    fail "Branch name ${HEAD_REF} does not follow the convention (feature/ft-NNN-slug, fix/slug, refactor/slug, test/slug, docs/slug, chore/slug, ci/slug)."
    ;;
  *)
    fail "PRs must target development (or main for releases and hotfixes), got ${BASE_REF}."
    ;;
esac
