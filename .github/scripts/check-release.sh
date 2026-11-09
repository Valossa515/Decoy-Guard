#!/usr/bin/env bash
# Guards for the Release workflow (CONTRIBUTING.md, Releases).
# Input: VERSION (X.Y.Z) and REF (the ref the workflow runs on).
set -euo pipefail

if [[ "${REF}" != "refs/heads/main" ]]; then
  echo "Releases are cut from main, got ${REF}. Choose the main branch in Run workflow."
  exit 1
fi

if [[ ! "${VERSION}" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Version must be X.Y.Z without a v prefix, got: ${VERSION}"
  exit 1
fi

if git rev-parse -q --verify "refs/tags/v${VERSION}" >/dev/null; then
  echo "Tag v${VERSION} already exists. Released versions are never reused."
  exit 1
fi

echo "OK: releasing v${VERSION} from main"
