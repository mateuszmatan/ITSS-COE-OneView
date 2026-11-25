#!/usr/bin/env bash
# Runs the scripts of the CI build of OneView against fakes, each in a copy of the files it reads:
# ci/prepare.sh with fake JDKs, fake Node.js and npm versions and fake Pythons, ci/build.sh with a
# fake Gradle wrapper, ci/changed-files.sh in a git repository, and ci/shared-files.sh against two
# fake branches of the library. The fake Pythons hide every Python name that ci/prepare.sh tries,
# so the Python of the machine is not used; PIP_NO_INDEX is set as well, so nothing is downloaded.
# Needs bash, git and node. Usage: bash ci/test/ci-scenarios.sh
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
T="$(cd "$(mktemp -d)" && pwd -P)"
trap 'rm -rf "$T"' EXIT
failures=0
check() {
  if [ "$2" = "0" ]; then
    echo "PASS  $1"
  else
    failures=$((failures + 1))
    echo "FAIL  $1"
    [ -n "${3:-}" ] && printf '%s\n' "$3" | head -20 | sed 's/^/      /'
  fi
}
has() { grep -qF -- "$2" "$1"; }
REAL_NODE="$(command -v node)"
if [ -z "$REAL_NODE" ] || ! command -v git > /dev/null 2>&1; then
  echo "CI SCENARIOS FAILED - they need node and git on the PATH"
  exit 1
fi

fake_java() {
  mkdir -p "$1/bin"
  cat > "$1/bin/java" <<EOF
#!/usr/bin/env bash
[ "\$1" = "-XshowSettings:properties" ] && echo "    java.home = $1" >&2
echo "openjdk version \"$2\" 2024-10-15" >&2
EOF
  chmod +x "$1/bin/java"
  if [ "${3:-}" != "jre" ]; then
    printf '#!/usr/bin/env bash\necho "javac %s"\n' "$2" > "$1/bin/javac"
    chmod +x "$1/bin/javac"
  fi
}
fake_python_name() {
  if [ "$4" = "-" ]; then
    printf '#!/usr/bin/env bash\necho "pyenv: %s: command not found" >&2\nexit 127\n' "$2" > "$1/bin/$2"
  else
    cat > "$1/bin/$2" <<EOF
#!/usr/bin/env bash
if [ "\$1" = "--version" ]; then echo "Python $4"; exit 0; fi
if [ "\$1 \${2:-}" = "-m venv" ] && [ "$3" = "yes" ]; then
  mkdir -p "\$3/bin"
  printf '#!/usr/bin/env bash\necho "ERROR: No matching distribution found for markdown" >&2\nexit 1\n' > "\$3/bin/python"
  chmod +x "\$3/bin/python"
  exit 0
fi
echo "Error: the venv module is not available" >&2
exit 1
EOF
  fi
  chmod +x "$1/bin/$2"
}
fake_python() {
  mkdir -p "$1/bin"
  for name in python3 python3.11 python3.12 python3.13 python3.14 python3.15; do
    fake_python_name "$1" "$name" "$2" "$3"
  done
}
fake_node() {
  mkdir -p "$1/bin"
  printf 'Object.defineProperty(process, "version", { value: "v%s" });\n' "$2" > "$1/version.js"
  printf '#!/usr/bin/env bash\nexec "%s" -r "%s" "$@"\n' "$REAL_NODE" "$1/version.js" > "$1/bin/node"
  printf '#!/usr/bin/env bash\necho %s\n' "$3" > "$1/bin/npm"
  chmod +x "$1/bin/node" "$1/bin/npm"
}
copy_ci() {
  mkdir -p "$1/frontend"
  cp -R "$ROOT/ci" "$ROOT/tools" "$1/"
  cp "$ROOT/frontend/package.json" "$1/frontend/"
}

fake_java "$T/jdk21" 21.0.5
fake_java "$T/jdk17" 17.0.9
fake_java "$T/path21" 21.0.2
fake_java "$T/path17" 17.0.9
fake_java "$T/jre21" 21.0.5 jre
mkdir -p "$T/shims"
printf '#!/usr/bin/env bash\nexec "%s" "$@"\n' "$T/path21/bin/java" > "$T/shims/java"
chmod +x "$T/shims/java"
fake_python "$T/py312" yes 3.12.4
fake_python "$T/py39" yes 3.9.18
fake_python "$T/pynovenv" no 3.12.4
fake_python "$T/pynewest" yes 3.11.9
fake_python_name "$T/pynewest" python3.13 yes 3.13.1
fake_python_name "$T/pynewest" python3.14 yes -
fake_python_name "$T/pynewest" python3.15 yes -
P="$T/py312/bin"
fake_node "$T/node20" 20.19.1 10.8.2
fake_node "$T/node22" 22.12.0 10.9.2
fake_node "$T/node18" 18.20.4 10.8.2
fake_node "$T/oldnpm" 20.19.1 10.2.4
mkdir -p "$T/empty"
R="$T/prepare"
copy_ci "$R"
prepare() {
  local out="$1"
  shift
  rm -f "$R/build/ci/env.sh"
  env -u ONEVIEW_CI_JAVA_HOME -u ONEVIEW_CI_NODE_HOME -u JAVA_HOME PIP_NO_INDEX=1 "$@" bash "$R/ci/prepare.sh" > "$out" 2>&1
}

prepare "$T/p1.log" PATH="$P:$T/path17/bin:$T/node18/bin:$PATH" ONEVIEW_CI_JAVA_HOME="$T/jdk21" ONEVIEW_CI_NODE_HOME="$T/node20"
status=$?
check "prepare: CI_JAVA_HOME and CI_NODE_HOME win over the PATH" \
  "$([ $status = 0 ] && has "$R/build/ci/env.sh" "export JAVA_HOME=\"$T/jdk21\"" && has "$R/build/ci/env.sh" "$T/jdk21/bin:$T/node20/bin" && has "$T/p1.log" 'Node.js v20.19.1, npm 10.8.2' && echo 0)" "$(cat "$T/p1.log")"
check "prepare: markdown that pip cannot install gives a [WARN] with the Python, and no Python on the PATH" \
  "$(has "$T/p1.log" "[WARN] markdown==3.11.3 could not be installed for Python 3.12.4 at $P/python3 (build/ci/venv.log)" && ! has "$R/build/ci/env.sh" 'build/ci/' && echo 0)" "$(cat "$T/p1.log" "$R/build/ci/env.sh")"

prepare "$T/p2.log" PATH="$P:$T/path17/bin:$T/node22/bin:$PATH" ONEVIEW_CI_JAVA_HOME="$T/jdk17" JAVA_HOME="$T/jdk21"
status=$?
check "prepare: a CI_JAVA_HOME without a JDK 21 falls back to JAVA_HOME with a [WARN]" \
  "$([ $status = 0 ] && has "$T/p2.log" "[WARN] CI_JAVA_HOME $T/jdk17 holds no JDK 21 (bin/java and bin/javac): JAVA_HOME is tried" && has "$R/build/ci/env.sh" "export JAVA_HOME=\"$T/jdk21\"" && echo 0)" "$(cat "$T/p2.log")"
check "prepare: node and npm on the PATH that fit the engines of frontend/package.json" \
  "$(has "$T/p2.log" 'Node.js v22.12.0, npm 10.9.2' && ! has "$R/build/ci/env.sh" "$T/node22" && echo 0)" "$(cat "$T/p2.log")"

prepare "$T/p3.log" PATH="$P:$T/path21/bin:$T/node20/bin:$PATH" ONEVIEW_CI_JAVA_HOME="$T/empty" JAVA_HOME="$T/jdk17" ONEVIEW_CI_NODE_HOME="$T/node18"
status=$?
check "prepare: then java on the PATH, and a CI_NODE_HOME of the wrong version falls back to the PATH" \
  "$([ $status = 0 ] && has "$T/p3.log" "[WARN] JAVA_HOME $T/jdk17 holds no JDK 21 (bin/java and bin/javac): java on the PATH is tried" && has "$R/build/ci/env.sh" "export JAVA_HOME=\"$T/path21\"" && has "$T/p3.log" "[WARN] CI_NODE_HOME $T/node18: Node.js v18.20.4 (wanted ^20.19.0 || ^22.12.0): node and npm on the PATH are used" && has "$T/p3.log" 'Node.js v20.19.1, npm 10.8.2' && echo 0)" "$(cat "$T/p3.log")"

prepare "$T/p4.log" PATH="$P:$T/path17/bin:$T/node20/bin:$PATH" JAVA_HOME="$T/jdk17"
status=$?
check "prepare: no JDK 21 anywhere fails the step" \
  "$([ $status = 1 ] && has "$T/p4.log" 'This agent lacks what the build of OneView calls: a JDK 21' && [ ! -f "$R/build/ci/env.sh" ] && echo 0)" "$(cat "$T/p4.log")"

prepare "$T/p5.log" PATH="$P:$T/path21/bin:$T/oldnpm/bin:$PATH" ONEVIEW_CI_NODE_HOME="$T/missing"
status=$?
check "prepare: a CI_NODE_HOME without node falls back to the PATH, and an old npm there fails the step" \
  "$([ $status = 1 ] && has "$T/p5.log" "[WARN] CI_NODE_HOME $T/missing holds no bin/node and bin/npm" && has "$T/p5.log" 'npm 10.2.4 (wanted >=10.8.2)' && echo 0)" "$(cat "$T/p5.log")"

prepare "$T/p6.log" PATH="$P:$T/path21/bin:$T/node20/bin:$PATH" ONEVIEW_CI_JAVA_HOME="$T/jdk21" JAVA_HOME="$T/jdk21" ONEVIEW_CI_NODE_HOME="$T/node20"
check "prepare: a value equal to the one of the agent gets an [INFO] line" \
  "$(has "$T/p6.log" '[INFO] CI_JAVA_HOME is the JAVA_HOME of the agent and can be left empty' && has "$T/p6.log" '[INFO] CI_NODE_HOME is the Node.js on the PATH of the agent and can be left empty' && echo 0)" "$(cat "$T/p6.log")"

prepare "$T/p7.log" PATH="$P:$T/path17/bin:$T/node20/bin:$PATH" ONEVIEW_CI_JAVA_HOME="$T/jre21" JAVA_HOME="$T/jdk21"
status=$?
check "prepare: a CI_JAVA_HOME with java 21 but without javac (a JRE) falls back to JAVA_HOME with a [WARN]" \
  "$([ $status = 0 ] && has "$T/p7.log" "[WARN] CI_JAVA_HOME $T/jre21 holds no JDK 21 (bin/java and bin/javac): JAVA_HOME is tried" && has "$R/build/ci/env.sh" "export JAVA_HOME=\"$T/jdk21\"" && echo 0)" "$(cat "$T/p7.log")"

prepare "$T/p8.log" PATH="$P:$T/shims:$T/node20/bin:$PATH"
status=$?
check "prepare: java on the PATH through a shim script gives the JAVA_HOME the JVM names (java.home)" \
  "$([ $status = 0 ] && has "$T/p8.log" "JDK 21 at $T/path21;" && has "$R/build/ci/env.sh" "export JAVA_HOME=\"$T/path21\"" && echo 0)" "$(cat "$T/p8.log")"

prepare "$T/p9.log" PATH="$P:$T/jre21/bin:$T/node20/bin:$PATH"
status=$?
check "prepare: a JRE 21 on the PATH is no JDK and fails the step" \
  "$([ $status = 1 ] && has "$T/p9.log" 'This agent lacks what the build of OneView calls: a JDK 21' && [ ! -f "$R/build/ci/env.sh" ] && echo 0)" "$(cat "$T/p9.log")"

prepare "$T/p10.log" PATH="$T/py39/bin:$T/path21/bin:$T/node20/bin:$PATH"
status=$?
check "prepare: without a Python 3.11 or later the step passes with a [WARN] and puts no Python on the PATH" \
  "$([ $status = 0 ] && has "$T/p10.log" '[WARN] No Python 3.11 or later, which markdown==3.11.3 needs: the pages of the shared user guides are not rendered again' && ! has "$R/build/ci/env.sh" 'build/ci/' && echo 0)" "$(cat "$T/p10.log" "$R/build/ci/env.sh")"

prepare "$T/p11.log" PATH="$T/pynovenv/bin:$T/path21/bin:$T/node20/bin:$PATH"
status=$?
check "prepare: a Python that cannot create a virtual environment is put first on the PATH as python3, with a [WARN]" \
  "$([ $status = 0 ] && has "$T/p11.log" "[WARN] $T/pynovenv/bin/python3 cannot create a virtual environment (build/ci/venv.log): it is put first on the PATH as python3" && has "$R/build/ci/env.sh" "$R/build/ci/bin:" && [ "$(readlink "$R/build/ci/bin/python3")" = "$T/pynovenv/bin/python3" ] && echo 0)" "$(cat "$T/p11.log" "$R/build/ci/env.sh")"

prepare "$T/p12.log" PATH="$T/pynewest/bin:$T/path21/bin:$T/node20/bin:$PATH"
check "prepare: the newest Python 3.11 or later is taken, and a Python name that does not start is passed over" \
  "$(has "$T/p12.log" "could not be installed for Python 3.13.1 at $T/pynewest/bin/python3.13 (build/ci/venv.log)" && echo 0)" "$(cat "$T/p12.log")"

B="$T/build"
copy_ci "$B"
cat > "$B/gradlew" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$(dirname "$0")/gradle-arguments.txt"
[ -n "${FAKE_GRADLE_CHANGE:-}" ] && echo changed >> "$(dirname "$0")/$FAKE_GRADLE_CHANGE"
echo "BUILD ${FAKE_GRADLE_EXIT:-0}"
exit "${FAKE_GRADLE_EXIT:-0}"
EOF
chmod +x "$B/gradlew"
printf 'build/\ngradle-arguments.txt\n' > "$B/.gitignore"
git -C "$B" init -q && git -C "$B" add -A \
  && git -C "$B" -c commit.gpgsign=false -c core.hooksPath=/dev/null -c user.name=ci -c user.email=ci@example.invalid commit -q -m fixture
printf '#!/usr/bin/env bash\n' > "$T/chromium" && chmod +x "$T/chromium"
build() {
  local out="$1"
  shift
  env -u ONEVIEW_CI_BROWSER -u ONEVIEW_CI_PERFORMANCE_FACTOR -u FAKE_GRADLE_CHANGE -u FAKE_GRADLE_EXIT "$@" bash "$B/ci/build.sh" > "$out" 2>&1
}

build "$T/b1.log"
status=$?
check "build: ./gradlew build without daemon, with the log in build/ci/build.log" \
  "$([ $status = 0 ] && [ "$(tr '\n' ' ' < "$B/gradle-arguments.txt")" = "--no-daemon --console=plain --continue build " ] && has "$B/build/ci/build.log" 'BUILD 0' && has "$T/b1.log" 'CHANGED FILES CHECK PASSED' && echo 0)" "$(cat "$T/b1.log")"

build "$T/b2.log" ONEVIEW_CI_BROWSER="$T/chromium" ONEVIEW_CI_PERFORMANCE_FACTOR=3.5
check "build: CI_BROWSER and CI_PERFORMANCE_FACTOR become system properties" \
  "$(has "$B/gradle-arguments.txt" "-Dfrontend.browser=$T/chromium" && has "$B/gradle-arguments.txt" '-Dperformance.factor=3.5' && echo 0)" "$(cat "$T/b2.log")"

build "$T/b3.log" ONEVIEW_CI_BROWSER="$T/none" ONEVIEW_CI_PERFORMANCE_FACTOR=fast
check "build: a wrong CI_BROWSER and CI_PERFORMANCE_FACTOR get a [WARN] and are not passed on" \
  "$(has "$T/b3.log" "[WARN] CI_BROWSER $T/none is not a program on this agent" && has "$T/b3.log" '[WARN] CI_PERFORMANCE_FACTOR fast is not a number of 1 or more' && ! has "$B/gradle-arguments.txt" '-Dfrontend.browser' && ! has "$B/gradle-arguments.txt" '-Dperformance.factor' && echo 0)" "$(cat "$T/b3.log")"

build "$T/b4.log" ONEVIEW_CI_PERFORMANCE_FACTOR=2
check "build: a CI_PERFORMANCE_FACTOR equal to the default gets an [INFO] line" \
  "$(has "$T/b4.log" '[INFO] CI_PERFORMANCE_FACTOR 2 is the default of the build and can be left empty' && has "$B/gradle-arguments.txt" '-Dperformance.factor=2' && echo 0)" "$(cat "$T/b4.log")"

build "$T/b5.log" FAKE_GRADLE_EXIT=1
status=$?
check "build: a failing Gradle build fails the step" "$([ $status = 1 ] && echo 0)" "$(cat "$T/b5.log")"

build "$T/b6.log" FAKE_GRADLE_CHANGE=ci/build.sh
status=$?
check "build: a run that changes a committed file fails, with the diff in build/ci/changed-files.diff" \
  "$([ $status = 1 ] && has "$T/b6.log" 'CHANGED FILES CHECK FAILED' && has "$T/b6.log" 'ci/build.sh' && has "$B/build/ci/changed-files.diff" '+changed' && echo 0)" "$(cat "$T/b6.log")"
git -C "$B" checkout -q -- ci/build.sh

SHARED="TODO.md docs/MANAGEMENT_OVERVIEW.md docs/USER_GUIDE_PORTAL.md docs/USER_GUIDE_PORTAL.html
docs/USER_GUIDE_WITHOUT_PORTAL.md docs/USER_GUIDE_WITHOUT_PORTAL.html tools/render-user-guides.sh
tools/render_doc.py tools/requirements-docs.txt tools/check-shared-files.sh"
DATABASE="database-product-config.yaml database-gradle-product-config.yaml database-maven-product-config.yaml"
S="$T/shared"
copy_ci "$S"
mkdir -p "$S/backend/src/regressionTest/resources/regression" "$T/wp/test/sandbox" "$T/main/test/oneview" "$T/main/test/fixtures/database/oneview"
touch "$S/backend/build.gradle" "$T/wp/test/sandbox/ConfigScenarios.groovy" "$T/main/test/oneview/query-scenarios.sh"
for file in $SHARED; do
  for folder in "$S" "$T/wp" "$T/main"; do
    mkdir -p "$folder/$(dirname "$file")"
    cp "$ROOT/$file" "$folder/$file"
  done
done
for file in $DATABASE; do
  cp "$ROOT/backend/src/regressionTest/resources/regression/$file" "$S/backend/src/regressionTest/resources/regression/$file"
  cp "$ROOT/backend/src/regressionTest/resources/regression/$file" "$T/main/test/fixtures/database/oneview/$file"
done
if bash "$S/tools/render-user-guides.sh" > /dev/null 2>&1; then
  for page in docs/USER_GUIDE_PORTAL.html docs/USER_GUIDE_WITHOUT_PORTAL.html; do
    cp "$S/$page" "$T/wp/$page"
    cp "$S/$page" "$T/main/$page"
  done
fi
bash "$S/ci/shared-files.sh" "$T/wp" "$T/main" > "$T/s1.log" 2>&1
status=$?
check "shared files: the same files in OneView and both library branches pass, with JUnit" \
  "$([ $status = 0 ] && has "$T/s1.log" 'SHARED FILES CHECK PASSED' && has "$S/build/ci/junit/shared-files.xml" 'failures="0"' && [ "$(grep -c '<testcase ' "$S/build/ci/junit/shared-files.xml")" -ge 23 ] && echo 0)" "$(cat "$T/s1.log")"
echo "a change" >> "$T/main/TODO.md"
bash "$S/ci/shared-files.sh" "$T/wp" "$T/main" > "$T/s2.log" 2>&1
status=$?
check "shared files: a file that differs fails, named in the log and in the JUnit file" \
  "$([ $status = 1 ] && has "$T/s2.log" "TODO.md differs from $T/main/TODO.md" && has "$S/build/ci/junit/shared-files.xml" 'failures="1"' && echo 0)" "$(cat "$T/s2.log")"

missing=""
for name in $(grep -o 'ONEVIEW_CI_[A-Z_]*' "$ROOT/ci/Jenkinsfile" | sort -u); do
  [ "$name" = "ONEVIEW_CI_AGENT" ] && continue
  grep -q -- "$name" "$ROOT"/ci/*.sh || missing="$missing $name"
done
check "Jenkinsfile: every variable it passes on is read by a script of ci/" "$([ -z "$missing" ] && echo 0)" "not read:$missing"

if [ "$failures" -gt 0 ]; then
  echo "CI SCENARIOS FAILED - $failures failing"
  exit 1
fi
echo "CI SCENARIOS PASSED"
