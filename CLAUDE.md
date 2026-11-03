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

The engine supports STU3 (3.0), R4 (4.0), R4B (4.3) and R5 (5.0). A mapping, though, runs on exactly
one version: the `spec.version` its context mapper declares (R4 if unset), and model mappers declare
theirs too.

* **Engine code** that reads, writes, validates or maps FHIR content, or a REST endpoint that accepts
  or returns it, must work on all four versions. It is checked on all four, or the report says which
  were not.
* **A mapping file** (a context or model mapper) is checked on the version it declares, and only on
  that one. Don't run it against the other versions or report them as unchecked.

## FHIRConnect spec

The engine implements the [FHIRConnect specification](https://github.com/SevKohler/FHIRconnect-spec).
For a change to what a mapping means, or to how the engine runs one, use the `fhirconnect-spec` skill
(`.claude/skills/fhirconnect-spec/`). It checks the change against the spec, the spec's issues and the
[mapping library](https://github.com/SevKohler/FHIRconnect-mapping-lib) before anything is decided.

Where the spec is unclear, draft an issue for its maintainers instead of settling the question in
engine code. Never file an issue, a comment or a PR in either repository without the user's
explicit go-ahead.

## Definition of done

A change is done when it was run, not when it reads right. Before calling it done:

* **Run the tier the change could break, and name the command in the report.**
  * The modules: `mvn clean verify`, which is what CI runs, with `TZ=Europe/Ljubljana`.
  * A REST-visible change: also the Newman collection, against a freshly built engine on a spare
    port: `newman run tests/e2e/1_openFHIR_Test.postman_collection.json --env-var baseUrl=http://localhost:<port>`.
    Without `--env-var` it targets `http://localhost:8080`, and the collection purges all state, so
    never run it like that where a developer instance may be on 8080.
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
* **Both directions.** A mapping change is checked for both toFHIR and toOpenEHR, or the report says
  which was not. The FHIR versions are covered under *FHIR versions* above.
* **Expected-output fixtures are edited, not re-serialised.** The KDS fixtures under
  `core/src/test/resources/kds/` carry `//` comments and their own spacing. Change only the lines
  that changed, and check that `git diff` shows nothing else.
* **Changelog and documentation** each have an outcome in the report, including "none".
* **Invented values are named.** The report calls out any limit, timeout or default nobody
  specified as your own choice.
* **When integrating subagent work, check the code, not the subagent's report.**

## Review

No change is its own only reviewer. Dispatch the `code-reviewer` agent
(`.claude/agents/code-reviewer.md`) **once, before the change is proposed for commit**. Don't run it
after every iteration or small follow-up; reviews cost time and tokens. Hand it:

* the scope: the files to review, and the files other sessions are touching;
* the build and test commands you ran, with their status;
* any output from a test that failed before the fix.

That way it doesn't have to re-derive them.

It may run in the background, but **don't edit the files under review until it reports**. It reads
the working tree, so an edit while it runs gives it half-edited code and stale line numbers.

Fix what it reports as must-change, or push back with evidence where a finding doesn't apply. Re-run
it on the fixes if they change logic. Otherwise list them in your report as **not reviewed**, so
nobody takes the committed version for the reviewed one. Skip the review for trivial changes (a
typo, a changelog-only edit, a comment), and say that you skipped it.
