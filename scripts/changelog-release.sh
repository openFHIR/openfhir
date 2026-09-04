#!/usr/bin/env bash
# Changelog helper for the release pipeline (also usable by hand).
#
# Usage:
#   scripts/changelog-release.sh check-unreleased    exit non-zero if '## Unreleased' is missing or empty
#   scripts/changelog-release.sh roll <version>      rename '## Unreleased' to '## [<version>] - <today>'
#                                                    and insert a fresh empty '## Unreleased' above it;
#                                                    fails if '## [<version>]' already exists
#   scripts/changelog-release.sh extract <version>   print the body of the '## [<version>]' section
#
# Operates on CHANGELOG.md in the repo root; override with CHANGELOG_FILE=<path>.
set -euo pipefail

FILE="${CHANGELOG_FILE:-$(cd "$(dirname "$0")/.." && pwd)/CHANGELOG.md}"
CMD="${1:-}"

usage() {
    sed -n 's/^#   //p' "$0" >&2
    exit 2
}

[ -f "$FILE" ] || { echo "ERROR: changelog not found: $FILE" >&2; exit 1; }

# Print the body of a section: exact "## Unreleased" heading, or prefix match
# for "## [<version>]" (released headings carry a trailing " - <date>").
# The file historically has CRLF line endings, so comparisons strip a trailing CR.
section_body() {
    local heading=$1
    awk -v h="$heading" '
        { line = $0; sub(/\r$/, "", line) }
        insec { if (line ~ /^## /) exit; print line; next }
        (h == "## Unreleased" && line == h) || (h != "## Unreleased" && index(line, h) == 1) { insec = 1 }
    ' "$FILE"
}

has_unreleased() {
    grep -q $'^## Unreleased\r\{0,1\}$' "$FILE"
}

case "$CMD" in
    check-unreleased)
        if ! has_unreleased; then
            echo "ERROR: no '## Unreleased' section in $FILE" >&2
            exit 1
        fi
        if ! section_body "## Unreleased" | grep -q '[^[:space:]]'; then
            echo "ERROR: '## Unreleased' section in $FILE is empty" >&2
            exit 1
        fi
        echo "'## Unreleased' section present and non-empty"
        ;;

    roll)
        VERSION="${2:-}"
        [ -n "$VERSION" ] || usage
        if grep -q "^## \[$VERSION\]" "$FILE"; then
            echo "ERROR: section '## [$VERSION]' already exists in $FILE" >&2
            exit 1
        fi
        if ! has_unreleased; then
            echo "ERROR: no '## Unreleased' section to roll in $FILE" >&2
            exit 1
        fi
        TODAY=$(date +%F)
        # inserted lines reuse the heading line's ending, so a CRLF file stays CRLF
        awk -v v="$VERSION" -v d="$TODAY" '
            { line = $0; sub(/\r$/, "", line) }
            line == "## Unreleased" && !done {
                eol = ($0 ~ /\r$/) ? "\r" : ""
                print "## Unreleased" eol
                print eol
                print "## [" v "] - " d eol
                done = 1
                next
            }
            { print }
        ' "$FILE" > "$FILE.tmp" && mv "$FILE.tmp" "$FILE"
        echo "Rolled '## Unreleased' into '## [$VERSION] - $TODAY'"
        ;;

    extract)
        VERSION="${2:-}"
        [ -n "$VERSION" ] || usage
        if ! grep -q "^## \[$VERSION\]" "$FILE"; then
            echo "ERROR: no section '## [$VERSION]' in $FILE" >&2
            exit 1
        fi
        # strip leading blank lines so the output starts at the first entry
        section_body "## [$VERSION]" | sed -e '/./,$!d'
        ;;

    *)
        usage
        ;;
esac
