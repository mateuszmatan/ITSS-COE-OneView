#!/usr/bin/env bash
# Fails when the build changed a file that git tracks, and writes the changes to
# build/ci/changed-files.diff: a page, a lock file or a fixture that the build writes again must
# be committed on the branch, so the CI refuses it.
# Usage: bash ci/changed-files.sh [repository folder]   (default: this repository)
cd "${1:-$(dirname "$0")/..}" || exit 1
mkdir -p build/ci
changed="$(git status --porcelain --untracked-files=no)"
if [ -n "$changed" ]; then
  echo "CHANGED FILES CHECK FAILED - the build changed committed files; run it on the branch and commit them:"
  echo "$changed" | sed 's/^/  /'
  git diff > build/ci/changed-files.diff
  exit 1
fi
echo "CHANGED FILES CHECK PASSED - the build changed no committed file"
