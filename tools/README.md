# tools

Scripts this repository relies on to prove its work, kept here so they change together with the
code they check. None of them is part of the plugin, and none of them is run by CI on its own.

## `corpus-gate.sh` and `advance-main.sh` — the corpus has to have run before `main` moves

The real test of the plugin is a corpus of real templates that lives outside this repository and
never enters it. CI cannot run it, so a green build says nothing about it. Two things make up for
that:

- **A stamp.** A whole run of `CorpusInspectionTest` over a corpus (`LATTE_CORPUS_DIR` set) writes a
  stamp into `<git-common-dir>/corpus-stamps/`: the commit it measured, whether the tree was clean,
  when, and what it counted. The stamp is never in git - it carries counts measured on somebody
  else's code - and its name is a hash of the corpus path, not the path. Without `LATTE_CORPUS_DIR`
  the corpus tests are reported as **skipped**, and the reason names the newest stamp.
- **A gate.** `corpus-gate.sh <commit>` accepts the commit only if every corpus named in
  `LATTE_CORPORA` (colon-separated paths) has a stamp taken over committed code on an ancestor of
  the commit, with nothing in `src/main` or `docs/latte` changed since, at most seven days old, over
  the whole corpus under its own Latte line, with no report of the plugin's own, no unclassified
  shape and no unreadable template.

`advance-main.sh <commit>` is how `main` moves: it refuses unless the move is a fast-forward of
`origin/main` without merge commits, the corpus gate passes, CI concluded `success` on that exact
commit, and `main` is not checked out in any working tree. Then it moves `main` and pushes that
commit - the one named, not whatever a branch points at by then.

A typical run, with the corpus paths only in the environment:

    LATTE_CORPUS_DIR=/path/to/corpus ./gradlew --no-daemon --no-build-cache --rerun-tasks \
      test --tests 'dev.noctud.latte.corpus.CorpusInspectionTest'
    # ...once per corpus, then:
    LATTE_CORPORA=/path/to/one:/path/to/another tools/advance-main.sh <commit>

`git-hooks/pre-push` runs the same gate for every push to `main`, including one made by hand or from
the IDE. Switch it on once per clone with `git config core.hooksPath tools/git-hooks`, and name the
corpora with `git config latte.corpora /path/to/one:/path/to/another` - that stays in `.git/config`
and never in a tracked file, and the gate reads it whenever `LATTE_CORPORA` is not set. `git push
--no-verify` goes round the hook; it guards against a mistake, not against intent.

The scripts and the hook are tested by `CorpusGateScriptTest`, `AdvanceMainScriptTest` and
`PrePushHookTest` against repositories and stamps made up for the purpose.

## `jacoco.init.gradle.kts` — line coverage for reviewing the tests

Applied from outside the build: coverage is an input to reviewing the tests, not a gate, and only a
zero means anything. Usage is in the file.
