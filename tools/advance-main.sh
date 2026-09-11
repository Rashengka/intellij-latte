#!/usr/bin/env bash
# Moves main to <commit> and pushes it - the only way main is meant to move.
#
#   LATTE_CORPORA=/path/to/one:/path/to/another tools/advance-main.sh <commit>
#
# It refuses, and leaves main where it is, unless
#
#   - main is not checked out in any working tree: moving a checked-out branch leaves that tree
#     showing the change reversed, and whoever works there builds on files that are gone,
#   - <commit> is a fast-forward of origin/main: if somebody else moved main, replaying on top of it
#     is a decision, not a step,
#   - the range holds no merge commit: a rebase would drop the merge and replay its commits as new,
#   - tools/corpus-gate.sh accepts <commit>: the corpora have run over what is being delivered,
#   - CI concluded success on exactly <commit>.
#
# The commit named is the one pushed, not whatever a branch points at by the time the checks end:
# on a checkout shared with other sessions a branch can move in between.
#
# Exit status: 0 main moved and was pushed, 1 refused, 2 the call itself was wrong.
set -euo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [ $# -ne 1 ]; then
    echo "usage: tools/advance-main.sh <commit>" >&2
    exit 2
fi
if ! target=$(git rev-parse --verify --quiet "$1^{commit}"); then
    echo "advance main: $1 is not a commit" >&2
    exit 2
fi

refuse() {
    echo "advance main: $*" >&2
    exit 1
}

if git worktree list --porcelain | grep -qx 'branch refs/heads/main'; then
    refuse "main is checked out in a working tree; moving it there would leave that tree showing the change reversed"
fi

git fetch -q origin main
if ! old=$(git rev-parse --verify --quiet refs/remotes/origin/main); then
    refuse "origin has no main to move"
fi
if ! git merge-base --is-ancestor "$old" "$target"; then
    refuse "${target:0:12} is not a fast-forward of origin/main (${old:0:12}); somebody moved main - rebase first"
fi
merges=$(git log --merges --format=%h "$old..$target")
if [ -n "$merges" ]; then
    refuse "the range holds a merge commit ($(echo "$merges" | tr '\n' ' ')); a rebase would replay it as new commits - decide by hand"
fi

if ! bash "$here/corpus-gate.sh" "$target"; then
    refuse "the corpus gate did not pass for ${target:0:12}, see above"
fi

# origin may be one of several remotes, and gh asks which repository is meant when it cannot tell.
repository=$(git remote get-url origin | sed -E 's#^(git@github\.com:|https://github\.com/)##; s#\.git$##')
if ! conclusions=$(gh run list --repo "$repository" --commit "$target" --json conclusion --jq '.[].conclusion' 2>&1); then
    refuse "cannot ask CI about ${target:0:12}: $conclusions"
fi
if ! printf '%s\n' "$conclusions" | grep -qx success; then
    refuse "CI has not concluded success on ${target:0:12} (got: $(printf '%s' "$conclusions" | tr '\n' ' '))"
fi

# Remote first: if origin refuses the push, nothing here has moved either.
git push -q origin "$target:refs/heads/main"
if current=$(git rev-parse --verify --quiet refs/heads/main); then
    git update-ref refs/heads/main "$target" "$current"
else
    git update-ref refs/heads/main "$target"
fi
echo "advance main: main is ${target:0:12}, pushed to origin (was ${old:0:12})"
