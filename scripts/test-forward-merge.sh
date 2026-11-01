#!/usr/bin/env bash
# Tests for scripts/forward-merge.sh, on throwaway git repositories (also usable by hand):
#
#   scripts/test-forward-merge.sh
#
# Each scenario builds a small repo whose release branch and develop diverge the way they do in
# practice, releases a patch on the release branch the way patch-release.yml does, and checks
# what scripts/forward-merge.sh makes of it. Needs git and mvn. The same file is in openfhir and
# openfhir-enterprise; each runs it with its own release-notes helper.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
if [ -f "$HERE/changelog-release.sh" ]; then
    NOTES=CHANGELOG.md; HELPER=changelog-release.sh
else
    NOTES=documentation/releases.rst; HELPER=release-notes.sh
fi
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
FAILURES=0

G() { git -c commit.gpgsign=false -c user.name=test -c user.email=test@localhost "$@"; }
mvnq() { mvn -B -q "$@" >/dev/null; }
prop() { sed -n "s#.*<$1>\(.*\)</$1>.*#\1#p" pom.xml | head -1; }

ok() { echo "  ok   $1"; }
fail() { echo "  FAIL $1"; FAILURES=$((FAILURES + 1)); }
expect() { # <description> <command...>
    local d=$1; shift
    if "$@" >/dev/null 2>&1; then ok "$d"; else fail "$d"; fi
}

pom() { # <version> <tomcat> <open-fhir>
    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>test</groupId>
    <artifactId>forward-merge-test</artifactId>
    <version>$1</version>
    <packaging>pom</packaging>

    <properties>
        <jackson-bom.version>2.19.4</jackson-bom.version>
        <tomcat.version>$2</tomcat.version>
        <open-fhir.version>$3</open-fhir.version>
        <jgroups>5.3.12.Final</jgroups>
    </properties>
</project>
EOF
}

initial_notes() {
    if [ "$NOTES" = CHANGELOG.md ]; then
        printf '# Changelog\n\n---\n\n## Unreleased\n\n## [1.0.0] - 2026-01-01\n### Added\n- the first release\n'
    else
        printf 'Release notes\n=============\n\nUnreleased\n----------\n\nRelease 1.0.0 (2026-01-01)\n--------------------------\n\nAdded\n^^^^^\n* the first release\n'
    fi
}

add_note() { # <text>: an entry under Unreleased
    awk -v t="$1" -v cl="$([ "$NOTES" = CHANGELOG.md ] && echo 1)" '
        { print }
        !done && cl && $0 == "## Unreleased" { print "- " t; done = 1 }
        !done && !cl && prev == "Unreleased" && $0 ~ /^-+$/ { print ""; print "* " t; done = 1 }
        { prev = $0 }
    ' "$NOTES" > "$NOTES.tmp" && mv "$NOTES.tmp" "$NOTES"
}

# a patch release on the current branch, as patch-release.yml makes it
release_patch() { # <version>
    if [ "$NOTES" = CHANGELOG.md ]; then
        scripts/changelog-release.sh roll "$1" >/dev/null
    else
        scripts/release-notes.sh stamp "$1" >/dev/null
    fi
    mvnq versions:set -DnewVersion="$1" -DgenerateBackupPoms=false
    mvnq versions:set-property -Dproperty=open-fhir.version -DnewVersion="$1" -DgenerateBackupPoms=false
    G commit -qam "Release $1"
    if [ "$NOTES" != CHANGELOG.md ]; then
        scripts/release-notes.sh prepare-next >/dev/null
        G commit -qam "Start the next patch release"
    fi
}

# main = release 1.0.0; release/1.0 cut from it; develop on 1.1.0-SNAPSHOT with a feature
new_repo() { # <name>
    local r="$WORK/$1"
    mkdir -p "$r/scripts" "$r/.github/workflows" "$r/src" "$(dirname "$r/$NOTES")"
    cd "$r"
    G init -q -b main
    cp "$HERE/forward-merge.sh" "$HERE/$HELPER" scripts/
    pom 1.0.0 10.1.55 1.0.0 > pom.xml
    initial_notes > "$NOTES"
    echo "tooling v1" > .github/workflows/tooling.yml
    echo "engine" > src/engine.txt
    G add -A && G commit -qm "Release 1.0.0"
    G branch release/1.0
    G switch -qc develop
    mvnq versions:set -DnewVersion=1.1.0-SNAPSHOT -DgenerateBackupPoms=false
    mvnq versions:set-property -Dproperty=open-fhir.version -DnewVersion=1.1.0-SNAPSHOT -DgenerateBackupPoms=false
    add_note "a develop feature"
    echo "feature" > src/feature.txt
    G add -A && G commit -qm "A feature"
}

fm() { # <args...>: run the script; sets RC
    RC=0
    scripts/forward-merge.sh --develop develop "$@" > "$WORK/out" 2>&1 || RC=$?
}

section_has() { # <file> <version> <text>: the section of <version> contains <text>
    if [ "$NOTES" = CHANGELOG.md ]; then
        CHANGELOG_FILE="$1" scripts/changelog-release.sh extract "$2" | grep -qF "$3"
    else
        RELEASES_FILE="$1" scripts/release-notes.sh extract "$2" | grep -qF "$3"
    fi
}
unreleased_has() { # <file> <text>
    if [ "$NOTES" = CHANGELOG.md ]; then
        awk -v t="$2" '/^## Unreleased/ { u = 1; next } /^## / { u = 0 } u && index($0, t) { f = 1 } END { exit !f }' "$1"
    else
        awk -v t="$2" 'prev == "Unreleased" && /^-+$/ { u = 1; prev = $0; next } /^-+$/ { u = 0 } u && index($0, t) { f = 1 } { prev = $0 } END { exit !f }' "$1"
    fi
}
section_before() { # <file> <version> <older>: the section of <version> comes before <older>'s
    local a b
    if [ "$NOTES" = CHANGELOG.md ]; then
        a=$(grep -n "^## \[$2\]" "$1" | cut -d: -f1); b=$(grep -n "^## \[$3\]" "$1" | cut -d: -f1)
    else
        a=$(grep -n "^Release $2 " "$1" | cut -d: -f1); b=$(grep -n "^Release $3 " "$1" | cut -d: -f1)
    fi
    [ -n "$a" ] && [ -n "$b" ] && [ "$a" -lt "$b" ]
}

echo "forward-merge tests ($NOTES)"

echo "scenario: a fix elsewhere merges, with the patch's release notes"
new_repo clean
G switch -q release/1.0
echo "fixed" > src/engine.txt; add_note "the fix"; G commit -qam "A fix"
release_patch 1.0.1
G switch -q develop
fm --worktree "$WORK/clean-fm" release/1.0 1.0.1
expect "exit 0" test "$RC" -eq 0
cd "$WORK/clean-fm"
expect "on branch forward-merge/1.0.1" test "$(git branch --show-current)" = forward-merge/1.0.1
expect "a merge commit" test "$(git rev-list --parents -1 HEAD | wc -w)" -eq 3
expect "develop's version kept" test "$(sed -n 's#^    <version>\(.*\)</version>#\1#p' pom.xml)" = 1.1.0-SNAPSHOT
expect "develop's open-fhir.version kept" test "$(prop open-fhir.version)" = 1.1.0-SNAPSHOT
expect "the fix is in" grep -q fixed src/engine.txt
expect "develop's feature is still in" test -f src/feature.txt
expect "Unreleased keeps develop's entry" unreleased_has "$NOTES" "a develop feature"
expect "the 1.0.1 section has the fix" section_has "$NOTES" 1.0.1 "the fix"
expect "1.0.1 sits above 1.0.0" section_before "$NOTES" 1.0.1 1.0.0
expect "main is an ancestor (release.yml needs it)" git merge-base --is-ancestor main HEAD
cd "$WORK/clean"

echo "scenario: --check reports the same and leaves nothing behind"
git worktree remove --force "$WORK/clean-fm"; G branch -qD forward-merge/1.0.1
fm --check release/1.0 1.0.1
expect "exit 0" test "$RC" -eq 0
expect "no forward-merge branch" test -z "$(git branch --list 'forward-merge/*')"
expect "no extra worktree" test "$(git worktree list | wc -l)" -eq 1
expect "the checkout is untouched" test -z "$(git status --porcelain)"

echo "scenario: a property bump next to a version line merges (the 3.0.4 enterprise pom conflict)"
new_repo neighbour
G switch -q release/1.0
sed -i 's#<tomcat.version>10.1.55<#<tomcat.version>10.1.61<#' pom.xml; add_note "tomcat 10.1.61"; G commit -qam "Tomcat"
release_patch 1.0.1
G switch -q develop
fm --check release/1.0 1.0.1
expect "exit 0" test "$RC" -eq 0
fm --worktree "$WORK/neighbour-fm" release/1.0 1.0.1
cd "$WORK/neighbour-fm"
expect "the bump is in" test "$(prop tomcat.version)" = 10.1.61
expect "develop's open-fhir.version kept" test "$(prop open-fhir.version)" = 1.1.0-SNAPSHOT
expect "only the bump and the notes differ from develop" test "$(git diff --name-only develop HEAD | sort | tr '\n' ' ')" = "$(printf '%s\n' "$NOTES" pom.xml | sort | tr '\n' ' ')"
cd "$WORK/neighbour"

echo "scenario: both bump the same property differently: left for a human"
new_repo real-conflict
sed -i 's#<tomcat.version>10.1.55<#<tomcat.version>10.1.60<#' pom.xml; G commit -qam "Tomcat on develop"
G switch -q release/1.0
sed -i 's#<tomcat.version>10.1.55<#<tomcat.version>10.1.61<#' pom.xml; add_note "tomcat 10.1.61"; G commit -qam "Tomcat"
release_patch 1.0.1
G switch -q develop
fm --worktree "$WORK/real-conflict-fm" release/1.0 1.0.1
expect "exit 2" test "$RC" -eq 2
expect "pom.xml listed" grep -q '`pom.xml`' "$WORK/out"
expect "the release notes are not left for a human" test "$(grep -c '^- `' "$WORK/out")" -eq 1
expect "merge left in progress" git -C "$WORK/real-conflict-fm" rev-parse -q --verify MERGE_HEAD
expect "the conflict is only the tomcat line" test "$(grep -c '^<<<<<<<' "$WORK/real-conflict-fm/pom.xml")" -eq 1
fm --check release/1.0 1.0.2
expect "--check: exit 2" test "$RC" -eq 2

echo "scenario: a fix PR checked before its release (--simulate-release)"
new_repo simulate
G switch -q release/1.0
sed -i 's#<tomcat.version>10.1.55<#<tomcat.version>10.1.61<#' pom.xml; add_note "tomcat 10.1.61"; G commit -qam "Tomcat"
G switch -q develop
fm --check release/1.0 1.0.1
expect "without simulating: the release notes need a human" test "$RC" -eq 2
fm --check --simulate-release release/1.0 1.0.1
expect "simulated: exit 0" test "$RC" -eq 0

echo "scenario: tooling cherry-picked onto the release branch (the 3.0.4 conflicts), then the next patch"
new_repo tooling
echo "tooling v2" > .github/workflows/tooling.yml; G commit -qam "Tooling v2"
TOOLING_V2=$(git rev-parse HEAD)
echo "tooling v3" > .github/workflows/tooling.yml; G commit -qam "Tooling v3"
G switch -q release/1.0
G cherry-pick -x "$TOOLING_V2" >/dev/null
sed -i 's#<tomcat.version>10.1.55<#<tomcat.version>10.1.61<#' pom.xml; add_note "tomcat 10.1.61"; G commit -qam "Tomcat"
release_patch 1.0.1
G switch -q develop
fm --worktree "$WORK/tooling-fm" release/1.0 1.0.1
expect "1.0.1: exit 2" test "$RC" -eq 2
expect "1.0.1: only the cherry-picked tooling is listed" test "$(grep -c '^- `' "$WORK/out")" -eq 1
expect "1.0.1: it is tooling.yml" grep -q '`.github/workflows/tooling.yml`' "$WORK/out"
cd "$WORK/tooling-fm"
G checkout --ours -- .github/workflows/tooling.yml && G add -A && G commit -q --no-edit
cd "$WORK/tooling"
G merge -q --no-ff --no-edit forward-merge/1.0.1
echo "tooling v4" > .github/workflows/tooling.yml; G commit -qam "Tooling v4"
G switch -q release/1.0
sed -i 's#<tomcat.version>10.1.61<#<tomcat.version>10.1.62<#' pom.xml; add_note "tomcat 10.1.62"; G commit -qam "Tomcat again"
release_patch 1.0.2
G switch -q develop
fm --worktree "$WORK/tooling-fm2" release/1.0 1.0.2
expect "1.0.2: exit 0 (once forward-merged, the tooling no longer conflicts)" test "$RC" -eq 0
cd "$WORK/tooling-fm2"
expect "1.0.2: develop's tooling kept" grep -q "tooling v4" .github/workflows/tooling.yml
expect "1.0.2: the second bump is in" test "$(prop tomcat.version)" = 10.1.62
expect "1.0.2 sits above 1.0.1" section_before "$NOTES" 1.0.2 1.0.1
cd "$WORK/tooling"

echo "scenario: an already forward-merged release"
fm --check release/1.0 1.0.1
G merge -q --no-ff --no-edit forward-merge/1.0.2
fm --check release/1.0 1.0.2
expect "exit 0" test "$RC" -eq 0
expect "says so" grep -q "already merged" "$WORK/out"

echo
if [ "$FAILURES" -gt 0 ]; then
    echo "$FAILURES check(s) failed"
    exit 1
fi
echo "all checks passed"
