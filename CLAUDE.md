# Working in openFHIR

Instructions for coding agents (and people) changing this repository.

## Changelog

Every change adds an entry to `CHANGELOG.md` in the same commit. The `changelog-check.yml` workflow
fails any PR that doesn't touch that file. The only way around it is the `no-changelog` label, for
changes with no user-visible effect.

* Use the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format. Add the entry under
  `## Unreleased`, in the right `### Added` / `### Changed` / `### Fixed` / `### Security`
  subsection, creating the subsection if it isn't there yet.
* Write it for someone upgrading the engine: what behaviour changed, not what code changed.
  Reference the issue number where there is one. Keep it **concise and to the point**.

## Design for more than one node

The engine can run as several nodes behind a load balancer, sharing one database. Design and review
**every** change as if more than one node runs:

* Anything in memory is per node: maps, caches, JVM locks (`ReentrantLock`, `synchronized`),
  counters and clocks. State that has to hold across the cluster goes through the database (a lease
  row, conditional updates) or a cluster-wide channel.
* A cache of database-backed state is evicted cluster-wide on every write, or keyed by something
  immutable (a commit sha, a content hash).
* `@Scheduled` jobs, event listeners and startup runners run on **every** node, possibly at the same
  moment. Make them safe to run concurrently, not just idempotent when run one after another.
* Concurrent requests for the same tenant can land on different nodes. Don't read a whole entity,
  do a slow step, then write the entity back. Don't order anything by one node's clock.

If a feature can't be implemented while staying safe on several nodes, don't implement it, and say so
clearly.

## FHIR versions

Every change must work for all supported FHIR versions: STU3 (3.0), R4 (4.0), R4B (4.3) and R5 (5.0).

## Definition of done

A change is done when it was run, not when it reads right. Before calling it done:

* **Run the tier the change could break, and name the command in the report.**
  * The modules: `mvn clean verify`, which is what CI runs, with `TZ=Europe/Ljubljana`.
  * A REST-visible change: also the Newman collection `tests/e2e/1_openFHIR_Test.postman_collection.json`,
    against a freshly built engine on a spare port. Never use 8080, because a running developer
    instance may be there and the collection purges all state.
  * If a tier could not be run, say which and why.
* **Never read a result through a pipe.** `mvn … | tail` reports `tail`'s exit status, so a failing
  build reads as passing. Redirect to a file and check `$?`, or use `set -o pipefail`. Quote the
  status and counts; don't characterise them.
* **A bug is fixed test-first.** Write the test at the layer where the defect shows, run it, and keep
  the failing output; it goes in the report. Had the test existed before the bug, it would have been
  red. If a test was only ever seen green, say so.
* **A new test must be able to fail.** Break the code the test guards (the code under test, not your
  fix) and watch the test go red, then restore it. Watch for loops and `allMatch` over collections
  that may be empty.
* **Every FHIR version and both directions.** A mapping change is checked for STU3, R4, R4B and R5,
  and for both toFHIR and toOpenEHR, or the report says which were not checked.
* **Expected-output fixtures are edited, not re-serialised.** The KDS fixtures under
  `core/src/test/resources/kds/` carry `//` comments, mixed CRLF/LF line endings and their own
  spacing. Change only the lines that changed, and check that `git diff` shows nothing else.
* **Changelog and documentation** each have an outcome in the report, including "none".
* **Invented values are named.** The report calls out any limit, timeout or default nobody
  specified as your own choice.
* **When integrating subagent work, check the code, not the subagent's report.**

## Review

No change is its own only reviewer. Dispatch the `code-reviewer` agent
(`.claude/agents/code-reviewer.md`) **once, before the change is proposed for commit**. Don't run it
after every iteration or small follow-up; reviews cost time and tokens. Run it in the background and
keep working. Hand it:

* the scope: the files to review, and the files other sessions are touching;
* the build and test commands you ran, with their status;
* any output from a test that failed before the fix.

That way it doesn't have to re-derive them.

Fix what it reports as must-change, or push back with evidence where a finding doesn't apply. Don't
re-review the fixes unless they were substantial. Skip the review for trivial changes (a typo, a
changelog-only edit, a comment), and say that you skipped it.
