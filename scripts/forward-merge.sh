#!/usr/bin/env bash
# Forward-merges a patch release into develop: the last step of patch-release.yml,
# the forward-merge check on fix PRs (forward-merge-check.yml), and what you run by
# hand when either reports files to resolve. The same file is in openfhir and
# openfhir-enterprise; keep the two copies identical.
#
# Usage:
#   scripts/forward-merge.sh [options] <release-ref> <version>
#
#   <release-ref>   the release branch with the patch released on it, e.g. origin/release/3.0
#                   (HEAD in patch-release.yml, before it pushes)
#   <version>       the patch version, e.g. 3.0.4
#
# Options:
#   --develop <ref>       the branch to merge into (default origin/develop)
#   --name <branch>       the release branch named in the merge commit (default <release-ref>
#                         without 'origin/')
#   --worktree <dir>      where to create the forward-merge worktree
#                         (default ../<repo>-forward-merge-<version>)
#   --check               only report: merge in a throwaway worktree, create no branch, keep nothing
#   --simulate-release    first do to <release-ref> what patch-release.yml does (release notes,
#                         versions), for checking a fix PR before the release exists
#
# Without --check it creates branch forward-merge/<version> from develop, with the merge commit
# on it, in a new worktree.
#
# How the merge is made (a merge, never a cherry-pick: release.yml needs main to be an ancestor
# of develop):
#   1. on top of the release, a commit sets develop's versions (project version, plus
#      open-fhir.version where develop's root pom has it), so the release's other pom changes
#      still merge;
#   2. that is merged into develop with --no-ff;
#   3. conflicts the patch flow always produces are resolved here:
#      - the release notes (CHANGELOG.md, or documentation/releases.rst): develop's file, with
#        the patch's section inserted above the newest release;
#      - documentation/conf.py: develop's (release.yml stamps it on the next release);
#      - a pom.xml: merged again against a base with develop's versions as well. Both sides
#        change the version lines on every patch, which makes a fix on a neighbouring line
#        conflict (a property bump next to <open-fhir.version>); against that base only real
#        edits can conflict.
#   Anything else is left for a human: a conflict there is a real one.
#
# Exit status: 0 merged (with --check: would merge) without a human; 2 files to resolve by hand,
# listed (without --check, the merge is left in progress in the worktree); 1 any other error.
set -euo pipefail

usage() {
    sed -n 's/^#   //p' "$0" >&2
    exit 1
}

DEVELOP_REF=origin/develop
NAME=""
WORKTREE=""
CHECK=false
SIMULATE=false
ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --develop) DEVELOP_REF="${2:?}"; shift 2 ;;
        --name) NAME="${2:?}"; shift 2 ;;
        --worktree) WORKTREE="${2:?}"; shift 2 ;;
        --check) CHECK=true; shift ;;
        --simulate-release) SIMULATE=true; shift ;;
        -h|--help) usage ;;
        -*) echo "ERROR: unknown option $1" >&2; usage ;;
        *) ARGS+=("$1"); shift ;;
    esac
done
[ ${#ARGS[@]} -eq 2 ] || usage
RELEASE_REF=${ARGS[0]}
VERSION=${ARGS[1]}
[ -n "$NAME" ] || NAME=${RELEASE_REF#origin/}

REPO=$(git rev-parse --show-toplevel)
RELEASE_SHA=$(git rev-parse --verify --quiet "$RELEASE_REF^{commit}") || { echo "ERROR: no such ref: $RELEASE_REF" >&2; exit 1; }
DEVELOP_SHA=$(git rev-parse --verify --quiet "$DEVELOP_REF^{commit}") || { echo "ERROR: no such ref: $DEVELOP_REF" >&2; exit 1; }
FM_BRANCH="forward-merge/$VERSION"

TMP=$(mktemp -d)
SOURCE_WT="$TMP/source"
cleanup() {
    git -C "$REPO" worktree remove --force "$SOURCE_WT" >/dev/null 2>&1 || true
    if $CHECK; then git -C "$REPO" worktree remove --force "$TMP/merge" >/dev/null 2>&1 || true; fi
    rm -rf "$TMP"
}
trap cleanup EXIT

# --check commits must neither prompt for a signing key nor need an identity
gitc() {
    if $CHECK; then
        git -c commit.gpgsign=false -c user.name=forward-merge-check -c user.email=forward-merge-check@localhost "$@"
    else
        git "$@"
    fi
}

summary() {
    echo "$1"
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "$1" >> "$GITHUB_STEP_SUMMARY"; fi
}

mvn_eval() { # <pom> <expression>: the value, or empty if not defined
    local v
    v=$(mvn -B -q -N -f "$1" help:evaluate -Dexpression="$2" -DforceStdout 2>/dev/null || true)
    case "$v" in null*|*"invalid expression"*|*ERROR*) v="" ;; esac
    echo "$v"
}

if git merge-base --is-ancestor "$RELEASE_SHA" "$DEVELOP_SHA"; then
    summary "$NAME ($VERSION) is already merged into $DEVELOP_REF: nothing to forward-merge"
    exit 0
fi

# ---- what develop looks like: the flavour of release notes, the versions to keep ----
if git cat-file -e "$DEVELOP_SHA:scripts/changelog-release.sh" 2>/dev/null; then
    NOTES=CHANGELOG.md
    git show "$DEVELOP_SHA:scripts/changelog-release.sh" > "$TMP/notes-helper.sh"
elif git cat-file -e "$DEVELOP_SHA:scripts/release-notes.sh" 2>/dev/null; then
    NOTES=documentation/releases.rst
    git show "$DEVELOP_SHA:scripts/release-notes.sh" > "$TMP/notes-helper.sh"
else
    echo "ERROR: develop has neither scripts/changelog-release.sh nor scripts/release-notes.sh" >&2
    exit 1
fi
chmod +x "$TMP/notes-helper.sh"
notes() { # <file> <helper args...>, with develop's copy of the helper
    local file=$1; shift
    if [ "$NOTES" = CHANGELOG.md ]; then CHANGELOG_FILE="$file" "$TMP/notes-helper.sh" "$@"; else RELEASES_FILE="$file" "$TMP/notes-helper.sh" "$@"; fi
}

git show "$DEVELOP_SHA:pom.xml" > "$TMP/develop-pom.xml"
DEV_VERSION=$(mvn_eval "$TMP/develop-pom.xml" project.version)
[ -n "$DEV_VERSION" ] || { echo "ERROR: could not read develop's project version" >&2; exit 1; }
PROPS=()
grep -q '<open-fhir.version>' "$TMP/develop-pom.xml" && PROPS+=(open-fhir.version)
declare -A DEV_PROP
for p in "${PROPS[@]}"; do DEV_PROP[$p]=$(mvn_eval "$TMP/develop-pom.xml" "$p"); done
echo "develop ($DEVELOP_REF) is on $DEV_VERSION${PROPS:+ (${PROPS[*]}: ${DEV_PROP[*]})}"

# ---- 1. the release side, with develop's versions ----
git -C "$REPO" worktree add -q --detach "$SOURCE_WT" "$RELEASE_SHA"
cd "$SOURCE_WT"
if $SIMULATE; then
    # what patch-release.yml does before it tags, with the release branch's own scripts
    if [ "$NOTES" = CHANGELOG.md ]; then
        scripts/changelog-release.sh roll "$VERSION" >/dev/null
    else
        if scripts/release-notes.sh stamp "$VERSION" >/dev/null 2>&1; then
            scripts/release-notes.sh prepare-next >/dev/null
        else
            echo "note: no release notes under 'Unreleased' to simulate the release of"
        fi
        [ -f documentation/conf.py ] && sed -i "s/^release = '.*'/release = '$VERSION'/" documentation/conf.py
    fi
    mvn -B -q versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
    gitc commit -qam "Simulated release $VERSION"
fi
mvn -B -q versions:set -DnewVersion="$DEV_VERSION" -DgenerateBackupPoms=false
for p in "${PROPS[@]}"; do
    mvn -B -q versions:set-property -Dproperty="$p" -DnewVersion="${DEV_PROP[$p]}" -DgenerateBackupPoms=false
done
if ! git diff --quiet; then
    gitc commit -qam "Set version $DEV_VERSION to forward-merge $VERSION into develop"
fi
SOURCE_SHA=$(git rev-parse HEAD)

# ---- 2. merge it into develop ----
if $CHECK; then
    MERGE_WT="$TMP/merge"
    git -C "$REPO" worktree add -q --detach "$MERGE_WT" "$DEVELOP_SHA"
else
    [ -n "$WORKTREE" ] || WORKTREE="$(dirname "$REPO")/$(basename "$REPO")-forward-merge-$VERSION"
    if git -C "$REPO" rev-parse --verify --quiet "refs/heads/$FM_BRANCH" >/dev/null; then
        echo "ERROR: branch $FM_BRANCH already exists; delete it (and its worktree) to start over" >&2
        exit 1
    fi
    git -C "$REPO" worktree add -q -b "$FM_BRANCH" "$WORKTREE" "$DEVELOP_SHA"
    MERGE_WT="$WORKTREE"
fi
cd "$MERGE_WT"
MERGE_OK=true
gitc merge --no-ff --no-commit -m "Merge $NAME ($VERSION) into develop" "$SOURCE_SHA" >/dev/null 2>&1 || MERGE_OK=false
if ! $MERGE_OK && [ -z "$(git diff --name-only --diff-filter=U)" ]; then
    echo "ERROR: git merge failed without conflicts:" >&2
    gitc merge --no-ff --no-commit "$SOURCE_SHA" >&2 || true
    exit 1
fi

# ---- 3. resolve what the patch flow always produces ----
UNRESOLVED=()
BASE_SHA=$(git merge-base HEAD MERGE_HEAD)
BASE_VERSION=""
declare -A BASE_PROP
read_base_versions() {
    [ -n "$BASE_VERSION" ] && return 0
    git show "$BASE_SHA:pom.xml" > "$TMP/base-pom.xml"
    BASE_VERSION=$(mvn_eval "$TMP/base-pom.xml" project.version)
    for p in "${PROPS[@]}"; do BASE_PROP[$p]=$(mvn_eval "$TMP/base-pom.xml" "$p"); done
}
# the merge base with develop's versions: the version lines are then unchanged on both sides
aligned_base() { # <path>
    read_base_versions
    local expr="s#<version>${BASE_VERSION//./\\.}</version>#<version>$DEV_VERSION</version>#g"
    for p in "${PROPS[@]}"; do
        [ -n "${BASE_PROP[$p]}" ] && expr="$expr; s#<$p>${BASE_PROP[$p]//./\\.}</$p>#<$p>${DEV_PROP[$p]}</$p>#g"
    done
    git show "$BASE_SHA:$1" | sed -e "$expr"
}

while IFS= read -r f; do
    [ -n "$f" ] || continue
    case "$f" in
        "$NOTES")
            git show "$SOURCE_SHA:$f" > "$TMP/release-notes-file"
            if notes "$TMP/release-notes-file" extract "$VERSION" > "$TMP/section" 2>/dev/null; then
                git checkout --ours -- "$f"
                if [ "$NOTES" = CHANGELOG.md ]; then notes "$f" insert-section "$VERSION" "$TMP/section" >/dev/null; else notes "$f" insert-release "$VERSION" "$TMP/section" >/dev/null; fi
                git add -- "$f"
                echo "resolved $f: develop's, with the $VERSION section inserted"
            else
                UNRESOLVED+=("$f")
            fi
            ;;
        documentation/conf.py)
            git checkout --ours -- "$f" && git add -- "$f"
            echo "resolved $f: develop's"
            ;;
        pom.xml|*/pom.xml)
            if git cat-file -e "$BASE_SHA:$f" 2>/dev/null; then
                aligned_base "$f" > "$TMP/base.xml"
                git show ":2:$f" > "$TMP/ours.xml"
                git show ":3:$f" > "$TMP/theirs.xml"
                if git merge-file -L develop -L "base, with develop's versions" -L "$NAME" -p \
                        "$TMP/ours.xml" "$TMP/base.xml" "$TMP/theirs.xml" > "$f"; then
                    git add -- "$f"
                    echo "resolved $f: merged against the base with develop's versions"
                else
                    UNRESOLVED+=("$f") # left with the conflict markers of that merge
                fi
            else
                UNRESOLVED+=("$f")
            fi
            ;;
        *)
            UNRESOLVED+=("$f")
            ;;
    esac
done < <(git diff --name-only --diff-filter=U)

if [ ${#UNRESOLVED[@]} -gt 0 ]; then
    summary "### Forward-merge of $NAME ($VERSION) into develop needs resolving by hand"
    summary ""
    for f in "${UNRESOLVED[@]}"; do summary "- \`$f\`"; done
    if $CHECK; then
        echo
        for f in "${UNRESOLVED[@]}"; do
            echo "---- $f"
            git diff -- "$f" | head -60
        done
        echo
        echo "These are real conflicts between $NAME and develop. Either change the PR so it merges"
        echo "into develop cleanly, or, if the conflict can't be avoided (both changed the same line),"
        echo "release with allow_manual_forward_merge and resolve it with scripts/forward-merge.sh."
    else
        echo
        echo "The merge is in progress in $MERGE_WT, the files above still have conflicts. To finish:"
        echo "  cd $MERGE_WT"
        echo "  # resolve the files above, then"
        echo "  git add <files> && git commit --no-edit"
        echo "  git push -u origin $FM_BRANCH"
        echo "  gh pr create --base develop --head $FM_BRANCH --title \"Forward-merge $VERSION into develop\""
        echo "Merge that PR with a merge commit, not squash or rebase."
    fi
    exit 2
fi

# ---- the result keeps develop's versions and has no conflict markers ----
if git grep -lE '^(<<<<<<<|>>>>>>>) ' -- . >/dev/null; then
    echo "ERROR: conflict markers left in: $(git grep -lE '^(<<<<<<<|>>>>>>>) ' -- . | tr '\n' ' ')" >&2
    exit 1
fi
MERGED_VERSION=$(mvn_eval pom.xml project.version)
[ "$MERGED_VERSION" = "$DEV_VERSION" ] || { echo "ERROR: the merge changed develop's version to $MERGED_VERSION" >&2; exit 1; }
for p in "${PROPS[@]}"; do
    v=$(mvn_eval pom.xml "$p")
    [ "$v" = "${DEV_PROP[$p]}" ] || { echo "ERROR: the merge changed develop's $p to $v" >&2; exit 1; }
done

gitc commit -q --no-edit
summary "### Forward-merge of $NAME ($VERSION) into develop merges without a human"
summary ""
summary "Changes it brings into develop:"
summary ""
summary '```'
summary "$(git diff --stat "$DEVELOP_SHA" HEAD)"
summary '```'
if ! $CHECK; then
    echo
    echo "Committed on $FM_BRANCH, in $MERGE_WT. To open the PR:"
    echo "  git -C $MERGE_WT push -u origin $FM_BRANCH"
    echo "  gh pr create --base develop --head $FM_BRANCH --title \"Forward-merge $VERSION into develop\""
    echo "Merge that PR with a merge commit, not squash or rebase."
fi
