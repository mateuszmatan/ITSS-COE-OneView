#!/usr/bin/env bash
# Runs the build of ITSS COE OneView for the CI build: ./gradlew build builds the jar and runs the
# backend and front-end unit tests, the regression, smoke and performance suites and the checks of
# the documentation and of ci/. Then ci/changed-files.sh fails the build when the run changed a
# committed file. ONEVIEW_CI_BROWSER names the Chromium or Chrome of the agent for the browser
# suites (frontend.browser), ONEVIEW_CI_PERFORMANCE_FACTOR widens the time limits of the
# performance suite (performance.factor, 2 by default); a wrong value gets a [WARN] line and is
# not passed on.
# Usage: bash ci/build.sh
cd "$(dirname "$0")/.." || exit 1
mkdir -p build/ci
[ -f build/ci/env.sh ] && . build/ci/env.sh
args=(--no-daemon --console=plain --continue)
browser="${ONEVIEW_CI_BROWSER:-}"
if [ -n "$browser" ]; then
  if [ -f "$browser" ] && [ -x "$browser" ]; then
    args+=("-Dfrontend.browser=$browser")
  else
    echo "[CI] [WARN] CI_BROWSER $browser is not a program on this agent: Playwright downloads its Chromium"
  fi
fi
factor="${ONEVIEW_CI_PERFORMANCE_FACTOR:-}"
if [ -n "$factor" ]; then
  if [[ "$factor" =~ ^[0-9]+([.][0-9]+)?$ ]] && awk -v f="$factor" 'BEGIN { exit !(f >= 1) }'; then
    if awk -v f="$factor" 'BEGIN { exit !(f == 2) }'; then
      echo "[CI] [INFO] CI_PERFORMANCE_FACTOR $factor is the default of the build and can be left empty"
    fi
    args+=("-Dperformance.factor=$factor")
  else
    echo "[CI] [WARN] CI_PERFORMANCE_FACTOR $factor is not a number of 1 or more: the default of the build (2) is used"
  fi
fi
echo "[CI] ./gradlew ${args[*]} build"
./gradlew "${args[@]}" build 2>&1 | tee build/ci/build.log
status=${PIPESTATUS[0]}
bash ci/changed-files.sh || status=1
exit $status
