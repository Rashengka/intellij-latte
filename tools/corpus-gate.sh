#!/usr/bin/env bash
# Decides whether a commit may become main, from the stamps the corpus tests leave behind.
#
#   LATTE_CORPORA=/path/to/one:/path/to/another tools/corpus-gate.sh <commit>
#
# or, once per clone and then for any push from anywhere:
#
#   git config latte.corpora /path/to/one:/path/to/another
#
# The corpora are real templates that live outside this repository and never enter it, so CI cannot
# check them and a green build says nothing about them. A whole run of CorpusInspectionTest over a
# corpus writes a stamp (see CorpusStamp); this script refuses a commit unless every corpus named in
# LATTE_CORPORA has a stamp that
#
#   - was taken over committed code, on an ancestor of <commit>,
#   - with nothing in src/main or docs/latte changed between that ancestor and <commit>,
#   - is at most seven days old, because a corpus that is a live repository changes on its own,
#   - covers the whole corpus under its own Latte line (no LATTE_CORPUS_LIMIT, no LATTE_CORPUS_VERSION),
#   - counted no report of the plugin's own, no unclassified shape and no unreadable template.
#
# Stamps are read from <git-common-dir>/corpus-stamps, or from LATTE_CORPUS_STAMP_DIR. Nothing here
# prints what is in a corpus; the paths printed are the ones LATTE_CORPORA was given.
#
# Exit status: 0 the commit may become main, 1 it may not, 2 the call itself was wrong.
set -euo pipefail

MAX_AGE_DAYS=7
PLUGIN_PATHS=(src/main docs/latte)

if [ $# -ne 1 ]; then
    echo "usage: tools/corpus-gate.sh <commit>" >&2
    exit 2
fi
if ! target=$(git rev-parse --verify --quiet "$1^{commit}"); then
    echo "corpus gate: $1 is not a commit" >&2
    exit 2
fi

stamp_dir=${LATTE_CORPUS_STAMP_DIR:-$(cd "$(git rev-parse --git-common-dir)" && pwd -P)/corpus-stamps}

failed=0
fail() {
    echo "corpus gate: $*" >&2
    failed=1
}

# The same id CorpusStamp.idOf gives: the first 16 hex digits of the SHA-256 of the real path.
sha256() {
    if command -v sha256sum > /dev/null; then sha256sum; else shasum -a 256; fi
}

now=$(date +%s)
checked=0
# The environment first, then the repository's own configuration: a push from the command line or
# the IDE carries no LATTE_CORPORA, and .git/config is a place for the paths that git never tracks.
corpora_list=${LATTE_CORPORA:-$(git config --get latte.corpora || true)}
IFS=: read -r -a corpora <<< "$corpora_list"

for corpus in ${corpora[@]+"${corpora[@]}"}; do
    [ -n "$corpus" ] || continue
    checked=$((checked + 1))
    name="corpus $checked ($corpus)"

    if [ ! -d "$corpus" ]; then
        fail "$name: not a directory"
        continue
    fi
    id=$(printf '%s' "$(cd "$corpus" && pwd -P)" | sha256 | cut -c1-16)
    file="$stamp_dir/$id.properties"
    if [ ! -f "$file" ]; then
        fail "$name: no stamp - run the corpus tests with LATTE_CORPUS_DIR set to it"
        continue
    fi

    field() { sed -n "s/^$1=//p" "$file" | head -n 1; }
    commit=$(field commit)
    dirty=$(field dirty)
    epoch=$(field epoch)
    plugin=$(field plugin)
    unclassified=$(field unclassified)
    unreadable=$(field unreadable)
    limit=$(field limit)
    forced=$(field forcedVersion)

    for number in "$epoch" "$plugin" "$unclassified" "$unreadable" "$limit"; do
        if ! [[ "$number" =~ ^[0-9]+$ ]]; then
            fail "$name: the stamp cannot be read ($file)"
            continue 2
        fi
    done

    if [ "$dirty" != "false" ]; then
        fail "$name: the stamp was taken over uncommitted changes in ${PLUGIN_PATHS[*]}"
    fi
    if ! git merge-base --is-ancestor "$commit" "$target" 2> /dev/null; then
        fail "$name: the stamped commit ${commit:0:12} is not an ancestor of ${target:0:12}"
    else
        changed=$(git diff --name-only "$commit" "$target" -- "${PLUGIN_PATHS[@]}")
        if [ -n "$changed" ]; then
            fail "$name: changed since the stamp: $(echo "$changed" | tr '\n' ' ')"
        fi
    fi
    if [ $((now - epoch)) -gt $((MAX_AGE_DAYS * 86400)) ]; then
        fail "$name: the stamp is $(((now - epoch) / 86400)) days old, at most $MAX_AGE_DAYS are allowed"
    fi
    if [ "$plugin" -ne 0 ]; then
        fail "$name: the plugin reported plugin=$plugin of its own"
    fi
    if [ "$unclassified" -ne 0 ]; then
        fail "$name: shapes nobody has placed, unclassified=$unclassified"
    fi
    if [ "$unreadable" -ne 0 ]; then
        fail "$name: templates that could not be read, unreadable=$unreadable"
    fi
    if [ "$limit" -ne 0 ]; then
        fail "$name: a sample, not the corpus (limit=$limit)"
    fi
    if [ -n "$forced" ]; then
        fail "$name: measured under a forced Latte line ($forced)"
    fi
done

if [ "$checked" -eq 0 ]; then
    echo "corpus gate: neither LATTE_CORPORA nor git config latte.corpora names a corpus, and a gate over nothing does not pass" >&2
    exit 1
fi
if [ "$failed" -ne 0 ]; then
    exit 1
fi
echo "corpus gate: passed for ${target:0:12} ($checked corpora)"
