#!/usr/bin/env bash
# Ranks a release version against the release tags (X.Y.Z) of this repository.
# Used by docker.yml (which moving image tags to push) and patch-release.yml
# (validation, whether to move main). Also usable by hand.
#
# Usage:
#   scripts/release-rank.sh is-newest <X.Y.Z>           exit 0 if <X.Y.Z> is the highest release overall
#   scripts/release-rank.sh is-newest-in-line <X.Y.Z>   exit 0 if <X.Y.Z> is the highest release of its X.Y line
#   scripts/release-rank.sh newest-in-line <X.Y>        print the highest X.Y.* release tag
#   scripts/release-rank.sh newest                      print the highest release tag overall
#
# <X.Y.Z> is ranked together with the existing tags, so it doesn't need to be
# tagged yet. Only plain X.Y.Z tags count. Reads the tags of the repository the
# script lives in; run with a full clone (fetch-depth: 0).
set -euo pipefail

CMD="${1:-}"
ARG="${2:-}"

usage() {
    sed -n 's/^#   //p' "$0" >&2
    exit 2
}

release_tags() {
    git -C "$(dirname "$0")" tag -l | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' || true
}

# highest of the release tags plus any extra versions given as arguments
highest() {
    { release_tags; printf '%s\n' "$@"; } | { grep . || true; } | sort -V | tail -n 1
}

require_version() {
    [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "ERROR: expected X.Y.Z, got: '$1'" >&2; exit 2; }
}

case "$CMD" in
    is-newest)
        require_version "$ARG"
        [ "$(highest "$ARG")" = "$ARG" ]
        ;;

    is-newest-in-line)
        require_version "$ARG"
        LINE="${ARG%.*}"
        NEWEST=$({ release_tags; echo "$ARG"; } | grep -E "^${LINE//./\\.}\.[0-9]+$" | sort -V | tail -n 1)
        [ "$NEWEST" = "$ARG" ]
        ;;

    newest-in-line)
        [[ "$ARG" =~ ^[0-9]+\.[0-9]+$ ]] || { echo "ERROR: expected X.Y, got: '$ARG'" >&2; exit 2; }
        NEWEST=$(release_tags | { grep -E "^${ARG//./\\.}\.[0-9]+$" || true; } | sort -V | tail -n 1)
        [ -n "$NEWEST" ] || { echo "ERROR: no release tag on line $ARG" >&2; exit 1; }
        echo "$NEWEST"
        ;;

    newest)
        NEWEST=$(highest)
        [ -n "$NEWEST" ] || { echo "ERROR: no release tags" >&2; exit 1; }
        echo "$NEWEST"
        ;;

    *)
        usage
        ;;
esac
