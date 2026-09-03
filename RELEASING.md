# Releasing openFHIR

Releases are cut by the **Release** GitHub Actions workflow
([.github/workflows/release.yml](.github/workflows/release.yml)) — no local
steps are required.

## What the workflow does

1. Checks out `main` and merges `origin/develop` into it (pom version conflicts
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
   - `docker.yml` → publishes `openfhir/openfhir:X.Y.Z` and moves `:latest`
6. Creates the GitHub Release with the changelog section as notes.
7. Bumps `develop`: merges `main` back, sets the next snapshot version, pushes
   (this re-triggers `publish.yml` to deploy the new SNAPSHOT — intended).

## Cutting a release

Prerequisites:

- `develop` is green and `## Unreleased` in `CHANGELOG.md` is complete —
  its content becomes the GitHub Release notes verbatim.
- The `RELEASE_TOKEN` repository secret exists (see below).

Steps:

1. GitHub → **Actions** → **Release** → **Run workflow**, with e.g.:
   - `release_version`: `3.0.0`
   - `next_snapshot_version`: `3.1.0-SNAPSHOT`
   - `dry_run`: `true` for a rehearsal (runs every gate, pushes nothing),
     then re-run with `false`.
2. After the run, verify:
   - GitHub Packages has `com.syntaric.openfhir:open-fhir:3.0.0`
   - Docker Hub has `openfhir/openfhir:3.0.0` and `:latest` points at it
   - The GitHub Release exists with the changelog notes
   - `develop` is on `3.1.0-SNAPSHOT`
3. **Releasing enterprise too? Wait for the "Publish Maven packages" run on the
   tag to finish first** — the enterprise release gate checks that the released
   OSS artifact exists in GitHub Packages.

## Failure model

Nothing irreversible happens before the gates pass: the merge, changelog roll
and version changes live only in the runner's clone. A gate failure leaves
`main`, `develop`, tags, Docker Hub and GitHub Packages untouched — fix the
problem on `develop` and re-dispatch with the same inputs.

After the push, the remaining steps (GitHub Release, develop bump) and the
downstream tag-triggered workflows are independently re-runnable from the
Actions UI; the release step itself skips an already-existing GitHub Release.

### Manual rollback (last resort)

```bash
git push origin :refs/tags/X.Y.Z          # delete the tag
gh release delete X.Y.Z                   # delete the GitHub Release
git revert -m 1 <release-merge-sha>       # revert main if needed
# re-run docker.yml from the Actions UI on the previous tag to restore :latest
```

Published Maven artifacts on GitHub Packages should be treated as immutable;
prefer rolling forward with a patch release.

## One-time setup

| Item | Where | Notes |
|---|---|---|
| `RELEASE_TOKEN` | Actions secret | Fine-grained PAT (or GitHub App token) with **Contents: read/write** on this repo. Required because pushes made with the default `GITHUB_TOKEN` do not trigger other workflows — the docker/publish workflows would never fire on the release tag. Needs bypass rights if `main` is protected. |
| `DOCKER_USERNAME` / `DOCKER_PASSWORD` | Actions secrets | Already in place for `docker.yml`. |
| `no-changelog` label | Repo labels | Lets a PR skip the changelog check. |

## Changelog conventions

`CHANGELOG.md` follows [keep-a-changelog](https://keepachangelog.com/): ongoing
work goes under `## Unreleased`; released sections are `## [X.Y.Z] - YYYY-MM-DD`.
The PR check requires a changelog entry unless the PR carries the
`no-changelog` label. `scripts/changelog-release.sh` supports
`check-unreleased`, `roll <version>` and `extract <version>` for manual use.
