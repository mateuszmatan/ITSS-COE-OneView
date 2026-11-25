#!/usr/bin/env bash
# Prepares the agent of a CI build of ITSS COE OneView: finds a JDK 21 and the Node.js and npm
# versions of "engines" in frontend/package.json, checks the commands the build calls, creates
# build/ci/venv with the newest Python 3.11 or later of the agent and the markdown version of
# tools/requirements-docs.txt (through PIP_INDEX_URL when it is set) for the check of the shared
# user guides, and writes build/ci/env.sh, which sets JAVA_HOME and puts the JDK, Node.js and that
# Python first on the PATH. A JDK 21 is a folder with bin/java and bin/javac of version 21.
# ONEVIEW_CI_JAVA_HOME and ONEVIEW_CI_NODE_HOME are tried first; a folder that does not hold the
# right version gets a [WARN] line and the next place is tried: JAVA_HOME, then java on the PATH,
# and node and npm on the PATH. A missing JDK, Node.js or npm fails this step, not the build; without
# a Python 3.11 or later the pages of the shared user guides are only not rendered again.
# Usage: bash ci/prepare.sh
cd "$(dirname "$0")/.." || exit 1
ROOT="$(pwd)"
mkdir -p build/ci
missing=()
for command in bash git awk sed grep; do
  command -v "$command" > /dev/null 2>&1 || missing+=("$command")
done

java_major() {
  "$1" -version 2>&1 | sed -n 's/.* version "\([0-9][0-9]*\).*/\1/p' | head -1
}

jdk21() {
  [ -n "$1" ] && [ -x "$1/bin/java" ] && [ -x "$1/bin/javac" ] && [ "$(java_major "$1/bin/java")" = "21" ]
}

java_home=""
for place in "CI_JAVA_HOME|${ONEVIEW_CI_JAVA_HOME:-}|JAVA_HOME" "JAVA_HOME|${JAVA_HOME:-}|java on the PATH"; do
  IFS='|' read -r name candidate next <<< "$place"
  [ -n "$candidate" ] || continue
  if jdk21 "$candidate"; then
    java_home="$candidate"
    break
  fi
  echo "[CI] [WARN] $name $candidate holds no JDK 21 (bin/java and bin/javac): $next is tried"
done
if [ -n "${ONEVIEW_CI_JAVA_HOME:-}" ] && [ "$java_home" = "$ONEVIEW_CI_JAVA_HOME" ] && [ "$java_home" = "${JAVA_HOME:-}" ]; then
  echo "[CI] [INFO] CI_JAVA_HOME is the JAVA_HOME of the agent and can be left empty"
fi
if [ -z "$java_home" ] && command -v java > /dev/null 2>&1 && [ "$(java_major java)" = "21" ]; then
  resolved="$(readlink -f "$(command -v java)" 2> /dev/null)"
  candidate="${resolved:+$(dirname "$(dirname "$resolved")")}"
  jdk21 "$candidate" || candidate="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.home = //p' | head -1)"
  jdk21 "$candidate" && java_home="$candidate"
fi
[ -n "$java_home" ] || missing+=("a JDK 21 (CI_JAVA_HOME, JAVA_HOME or java on the PATH)")

node_bin=""
if [ -n "${ONEVIEW_CI_NODE_HOME:-}" ]; then
  if [ -x "$ONEVIEW_CI_NODE_HOME/bin/node" ] && [ -x "$ONEVIEW_CI_NODE_HOME/bin/npm" ]; then
    node_bin="$ONEVIEW_CI_NODE_HOME/bin"
  else
    echo "[CI] [WARN] CI_NODE_HOME $ONEVIEW_CI_NODE_HOME holds no bin/node and bin/npm: node and npm on the PATH are used"
  fi
fi
engines() {
  PATH="$1${1:+:}$PATH" node -e '
    const wanted = require(process.argv[1]).engines || {};
    const parse = (text) => text.replace(/^v/, "").split(".").map(Number);
    const atLeast = (v, m) => v[0] > m[0] || (v[0] === m[0] && (v[1] > m[1] || (v[1] === m[1] && v[2] >= m[2])));
    const fits = (version, range) => !range || range.split("||").some((part) => {
      const found = /^\s*(\^|>=)(\d+)\.(\d+)\.(\d+)\s*$/.exec(part);
      if (!found) return true;
      const v = parse(version);
      const m = found.slice(2).map(Number);
      return atLeast(v, m) && (found[1] === ">=" || v[0] === m[0]);
    });
    const npm = require("child_process").execSync("npm --version").toString().trim();
    const wrong = [];
    if (!fits(process.version, wanted.node)) wrong.push("Node.js " + process.version + " (wanted " + wanted.node + ")");
    if (!fits(npm, wanted.npm)) wrong.push("npm " + npm + " (wanted " + wanted.npm + ")");
    console.log(wrong.length ? "wrong " + wrong.join(", ") : "ok Node.js " + process.version + ", npm " + npm);
  ' "$ROOT/frontend/package.json" 2> /dev/null
}
node_check=""
if [ -n "$node_bin" ]; then
  node_check="$(engines "$node_bin")"
  if [ "${node_check%% *}" != "ok" ]; then
    reason="${node_check#wrong }"
    echo "[CI] [WARN] CI_NODE_HOME $ONEVIEW_CI_NODE_HOME: ${reason:-node does not start}: node and npm on the PATH are used"
    node_bin=""
  elif [ "$(command -v node 2> /dev/null)" = "$node_bin/node" ]; then
    echo "[CI] [INFO] CI_NODE_HOME is the Node.js on the PATH of the agent and can be left empty"
  fi
fi
if [ -z "$node_bin" ]; then
  if command -v node > /dev/null 2>&1 && command -v npm > /dev/null 2>&1; then
    node_check="$(engines "")"
    reason="${node_check#wrong }"
    [ "${node_check%% *}" = "ok" ] || missing+=("${reason:-a Node.js that starts}")
  else
    missing+=("node and npm (CI_NODE_HOME or the PATH)")
  fi
fi

python_minor() {
  "$1" --version 2>&1 | sed -n 's/^Python 3\.\([0-9][0-9]*\).*/\1/p' | head -1
}
python=""
best=10
for candidate in python3 python3.11 python3.12 python3.13 python3.14 python3.15; do
  command -v "$candidate" > /dev/null 2>&1 || continue
  minor="$(python_minor "$candidate")"
  [ -n "$minor" ] && [ "$minor" -gt "$best" ] || continue
  best="$minor"
  python="$(command -v "$candidate")"
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "[CI] This agent lacks what the build of OneView calls: ${missing[*]}"
  exit 1
fi
echo "[CI] JDK 21 at $java_home; ${node_check#ok }; $(git --version)"

bin=""
rm -rf build/ci/venv build/ci/bin
wanted="$(cat tools/requirements-docs.txt)"
if [ -z "$python" ]; then
  echo "[CI] [WARN] No Python 3.11 or later, which $wanted needs: the pages of the shared user guides are not rendered again"
elif "$python" -m venv build/ci/venv > build/ci/venv.log 2>&1; then
  if build/ci/venv/bin/python -m pip install --disable-pip-version-check -q -r tools/requirements-docs.txt >> build/ci/venv.log 2>&1; then
    bin="$ROOT/build/ci/venv/bin"
    echo "[CI] $("$bin/python" -c 'import markdown; print("markdown " + markdown.__version__)') installed in build/ci/venv for $("$python" --version 2>&1) at $python"
  else
    echo "[CI] [WARN] $wanted could not be installed for $("$python" --version 2>&1) at $python (build/ci/venv.log): the pages of the shared user guides are rendered again only when python3 on the PATH has that version"
  fi
else
  echo "[CI] [WARN] $python cannot create a virtual environment (build/ci/venv.log): it is put first on the PATH as python3, and the pages of the shared user guides are rendered again only when it has $wanted"
  mkdir -p build/ci/bin
  ln -s "$python" build/ci/bin/python3
  bin="$ROOT/build/ci/bin"
fi
{
  printf 'export JAVA_HOME="%s"\n' "$java_home"
  printf 'export PATH="%s"\n' "$java_home/bin${node_bin:+:$node_bin}${bin:+:$bin}:\$PATH"
} > build/ci/env.sh
