#!/usr/bin/env bash
# Builds a small repo with jit, then lets real git inspect it.
# jit writes git's exact formats, so `git --git-dir=.jit` must read everything and agree with jit.
# Usage: mvn package && scripts/git-compat.sh
set -euo pipefail

JAR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/target/jit.jar"
[ -f "$JAR" ] || { echo "missing $JAR: run 'mvn package' first"; exit 1; }
jit()     { java -jar "$JAR" "$@"; }
git_jit() { git --git-dir=.jit --work-tree=. -c core.excludesFile=.jitignore "$@"; }   # real git, on jit's repo

failures=0
check() {   # check "description" command...
    if "${@:2}"; then echo "ok   $1"; else echo "FAIL $1"; failures=$((failures + 1)); fi
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"
for who in AUTHOR COMMITTER; do
    export "JIT_${who}_NAME=CI" "JIT_${who}_EMAIL=ci@example.com" "JIT_${who}_DATE=1700000000 +0000"
done

jit init > /dev/null
mkdir -p src/util target
echo "hello" > README.md
echo "class A {}" > src/A.java
echo "class U {}" > src/util/U.java
printf '#!/bin/sh\necho run\n' > run.sh && chmod +x run.sh
ln -s README.md link
printf 'target/\n*.log\n' > .jitignore
echo "build output" > target/A.class
echo "noise" > debug.log
jit add . && jit commit -m "first" > /dev/null
echo "more" >> README.md && jit add README.md && jit commit -m "second" -m "with a body" > /dev/null

# a few unstaged / untracked changes for status to find
echo "changed" >> src/A.java
rm src/util/U.java
echo "new" > new.txt

check "git fsck accepts every object and ref"   git_jit fsck --strict --no-progress
check "same log"                                diff <(jit log) <(git_jit log)
check "same index (ls-files -s)"                diff <(jit ls-files -s) <(git_jit ls-files -s)
check "same tree for HEAD"                      diff <(jit ls-tree -r HEAD) <(git_jit ls-tree -r HEAD)
check "same status"                             diff <(jit status --porcelain) <(git_jit status --porcelain | grep -v '^?? .jit/$')
check "same write-tree"                         [ "$(jit write-tree)" = "$(git_jit write-tree)" ]

[ "$failures" -eq 0 ] && echo "jit and git agree" || { echo "$failures check(s) failed"; exit 1; }
