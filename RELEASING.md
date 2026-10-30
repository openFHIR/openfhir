# Releasing openFHIR

openFHIR follows [Semantic Versioning](https://semver.org/) and the release
policy in [SECURITY.md](SECURITY.md):

| Change | Release | Cut from | Workflow |
|---|---|---|---|
| Features (and the fixes on develop) | minor `X.Y.0` | `develop` | **Release** ([release.yml](.github/workflows/release.yml)) |
| Breaking changes | major `X.0.0` | `develop` | **Release** |
| Fixes only (security or bug) | patch `X.Y.Z+1` | `release/X.Y` | **Patch Release** ([patch-release.yml](.github/workflows/patch-release.yml)), see [Patch (security) releases](#patch-security-releases) |

`develop` always carries the next minor's snapshot (`X.Y+1.0-SNAPSHOT`), so a
release from develop is a minor (or major) release, never a patch. No local
steps are required for either workflow.

## What the workflow does

1. Checks out `main`, checks that `develop` contains it (it doesn't while a
   patch release's forward-merge PR is open: merge that first), and merges
   `origin/develop` into it (pom version conflicts
   are auto-resolved in develop's favour; any other conflict aborts the run).
2. Rolls `CHANGELOG.md`: `## Unreleased` becomes `## [X.Y.Z] - <today>` and a
   fresh empty `## Unreleased` is inserted above it
   (via [scripts/changelog-release.sh](scripts/changelog-release.sh)).
3. Sets the release version across all module poms (`mvn versions:set`).
4. **Gates** — `mvn clean verify` plus the newman smoke collection
   (`tests/e2e/1_openFHIR_Test.postman_collection.json`) against a fresh
   mongo:7. Nothing is pushed until these pass.
5. Commits `Release X.Y.Z`, tags `X.Y.Z`, pushes `main` + tag. The tag push
   triggers:
   - `publish.yml` → deploys `X.Y.Z` to GitHub Packages
   - `docker.yml` → publishes `openfhir/openfhir:X.Y.Z` with SBOM and
     provenance attestations, and moves `:X.Y` and `:latest` (each only if this
     is the newest release of its line / overall, see `scripts/release-rank.sh`)
6. Creates the GitHub Release with the changelog section as notes and the
   CycloneDX SBOM (`target/bom.json`) attached.
7. Bumps `develop`: merges `main` back, sets the next snapshot version, pushes
   (this re-triggers `publish.yml` to deploy the new SNAPSHOT — intended).

## Cutting a release

Prerequisites:

- `develop` is green and `## Unreleased` in `CHANGELOG.md` is complete —
  its content becomes the GitHub Release notes verbatim.
- The `RELEASE_TOKEN` repository secret exists (see below).

Steps:

1. GitHub → **Actions** → **Release** → **Run workflow**:
   - "Use workflow from": **`develop`**. This only selects which copy of
     `release.yml` runs — the pipeline always checks out and releases `main`
     (with develop merged in), whatever you pick here. Pick `develop` because
     it always has the current version of the workflow; `main` only receives it
     as a side effect of a release.
   - `release_version`: `3.1.0` (develop's snapshot version without
     `-SNAPSHOT`: a minor release; a major only for breaking changes)
   - `next_snapshot_version`: `3.2.0-SNAPSHOT` (the next minor)
   - `dry_run`: `true` for a rehearsal (runs every gate, pushes nothing),
     then re-run with `false`.
2. After the run, verify:
   - GitHub Packages has `com.syntaric.openfhir:open-fhir:3.1.0`
   - Docker Hub has `openfhir/openfhir:3.1.0`, and `:3.1` and `:latest` point at it
   - The GitHub Release exists with the changelog notes and `bom.json`
   - `develop` is on `3.2.0-SNAPSHOT`
3. Update [SECURITY.md](SECURITY.md) "Supported versions" and the `line`
   matrix in [security-scan.yml](.github/workflows/security-scan.yml): the
   new line is supported, the previous one isn't (after a major: it keeps
   security patches for 6 months).

## Patch (security) releases

A patch release contains only fixes, and is built from the last release of its
line, independently of develop. Only the newest line gets patches (plus, for 6
months after a major, the last line of the previous major: security fixes only).

### Release branches

- `release/X.Y` is created **by hand, from the last tag of the line**, when the
  first fix for that line is needed. The `release/*` branch protection rule
  applies to it automatically:

  ```bash
  git switch -c release/3.0 3.0.3
  git cherry-pick <release tooling commit>   # see below, until the line's tag has it
  git push -u origin release/3.0
  ```

  The branch needs the release tooling: `scripts/release-rank.sh`, the current
  `docker.yml` (the image of a tag is built with the workflow file **from the
  tag**) and the CycloneDX plugin in the root `pom.xml`. Lines released from
  3.1.0 on have it already; for 3.0, cherry-pick the commit that introduced it.
- A fix is a PR against `release/X.Y` with a `CHANGELOG.md` entry under
  `## Unreleased`, in `### Security` or `### Fixed`. A fix that is also needed on
  develop reaches it through the forward-merge, so don't open a second PR.
- **Rolling-upgradable:** the nodes of an HA cluster are upgraded one at a time,
  so old and new patch run side by side on one database. A patch therefore has
  no database migrations (`db/.../mongodb/migrations/`, enforced by the
  workflow) and no change to the messages nodes exchange (JGroups cluster
  messages, cache-invalidation events; a review rule). Anything that needs
  either ships in the next minor.
- **Undisclosed vulnerabilities:** don't fix them in a public PR, which would
  disclose them. Develop the fix in the advisory's temporary private fork
  (Security → Advisories → the draft → *Start a temporary private fork*) and
  merge it into `release/X.Y` from there. Actions don't run in that fork, so
  run `mvn clean verify` locally; the patch workflow runs every gate again.

### Cutting a patch release

1. GitHub → **Actions** → **Patch Release** → **Run workflow**:
   - "Use workflow from": **`develop`** (the current copy of the workflow; the
     workflow checks out the release branch whatever you pick here)
   - `release_branch`: `release/3.0`
   - `release_version`: `3.0.4` (higher than the newest tag of the line)
   - `dry_run`: `true` first (runs every gate and the forward-merge, pushes
     nothing), then `false`.
2. The workflow:
   1. validates the inputs: the branch is `release/X.Y`, the version is on that
      line, isn't tagged yet and is higher than the newest tag of the line;
   2. fails if anything under `*/migrations/*` changed since that tag;
   3. rolls the changelog, sets the version, and runs the same gates as
      `release.yml` (`mvn clean verify`, newman smoke collection);
   4. commits `Release X.Y.Z`, tags it, pushes the branch and the tag
      (→ `publish.yml`, `docker.yml`). If this is the newest release overall,
      it also fast-forwards `main` to it;
   5. creates the GitHub Release with the notes and `bom.json` (not marked as
      *latest* when an older line is patched);
   6. opens the **forward-merge PR** `forward-merge/X.Y.Z` → `develop`: a merge
      of the release branch (never a cherry-pick), with develop's pom versions
      and the patch's changelog section inserted above develop's newest
      release. Other conflicts are left for you: the run fails after pushing
      the branch and prints the commands to finish the merge by hand.
3. **Merge the forward-merge PR with a merge commit** (not squash, not rebase).
   Until it is merged, `release.yml` refuses to run: `main` must be an
   ancestor of `develop`.
4. Publish the GitHub Security Advisory (with the CVE GitHub assigned) for each
   vulnerability the patch fixes, once the tag and images are out, and credit
   the reporter if they wish.
5. Verify: GitHub Packages has `X.Y.Z`; Docker Hub has `:X.Y.Z`, `:X.Y` moved to
   it, and `:latest` only if it's the newest release; the GitHub Release has
   `bom.json`.

A finding of [security-scan.yml](.github/workflows/security-scan.yml) (weekly
Trivy scan of the supported images) on a supported line is fixed the same way,
even when the only change is a dependency or base-image bump.

## Failure model

Both workflows share the `release` concurrency group, so they never run at the
same time. Nothing irreversible happens before the gates pass: the merge, changelog roll
and version changes live only in the runner's clone. A gate failure leaves
`main`, `develop`, tags, Docker Hub and GitHub Packages untouched — fix the
problem on `develop` (on `release/X.Y` for a patch) and re-dispatch with the
same inputs.

After the push, the remaining steps (GitHub Release, develop bump) and the
downstream tag-triggered workflows are independently re-runnable from the
Actions UI; the release step itself skips an already-existing GitHub Release.

### Manual rollback (last resort)

```bash
git push origin :refs/tags/X.Y.Z          # delete the tag
gh release delete X.Y.Z                   # delete the GitHub Release
git revert -m 1 <release-merge-sha>       # revert main if needed
```

Then point the moving image tags back at the newest remaining release. Re-run
`docker.yml` on that tag (it rebuilds the image and only moves `:X.Y` /
`:latest` where `release-rank.sh` says so), or retag without rebuilding:

```bash
NEWEST=$(scripts/release-rank.sh newest)                  # after deleting the tag locally too
LINE_NEWEST=$(scripts/release-rank.sh newest-in-line X.Y)
docker buildx imagetools create -t openfhir/openfhir:latest "openfhir/openfhir:$NEWEST"
docker buildx imagetools create -t openfhir/openfhir:X.Y "openfhir/openfhir:$LINE_NEWEST"
```

Published Maven artifacts on GitHub Packages should be treated as immutable;
prefer rolling forward with a patch release.

## One-time setup

| Item | Where | Notes |
|---|---|---|
| `RELEASE_TOKEN` | Actions secret | Fine-grained PAT (or GitHub App token) with **Contents: read/write** on this repo. Required because pushes made with the default `GITHUB_TOKEN` do not trigger other workflows — the docker/publish workflows would never fire on the release tag. Needs bypass rights if `main` is protected. |
| `DOCKER_USERNAME` / `DOCKER_PASSWORD` | Actions secrets | Already in place for `docker.yml`. |
| `no-changelog` label | Repo labels | Lets a PR skip the changelog check. |
| `release/*` branch protection | Settings → Branches | Same rules as `develop`, not `main`: `main` is locked, which would stop fix PRs from merging. Admins aren't enforced, so `RELEASE_TOKEN` (an admin's token, as for `main` and `develop`) can push the unsigned release commit directly. |
| Private vulnerability reporting | Settings → Code security | Enabled, so reporters can use *Report a vulnerability* (SECURITY.md). |
| Merge commits allowed | Settings → General → Pull Requests | Forward-merge PRs must be merged with a merge commit. |

## Changelog conventions

`CHANGELOG.md` follows [keep-a-changelog](https://keepachangelog.com/): ongoing
work goes under `## Unreleased`; released sections are `## [X.Y.Z] - YYYY-MM-DD`.
The PR check requires a changelog entry unless the PR carries the
`no-changelog` label. `scripts/changelog-release.sh` supports
`check-unreleased`, `roll <version>`, `extract <version>` and
`insert-section <version> <file>` (used by the forward-merge) for manual use.
