# Security Policy

## Supported versions

| Version | Supported                        |
|---------|----------------------------------|
| 3.0.x   | ✅ until 3.1.0 is released        |
| < 3.0   | ❌                                |

Only the newest minor line (`X.Y`) receives patch releases. When a new major version is released, the last minor line
of the previous major keeps receiving **security** patches for 6 more months.

Upgrades within a major version are free and need no changes to your environment: breaking changes only ship in major
releases (see [Release policy](#release-policy)). Staying on the newest minor line is therefore the supported way to
receive fixes.

## Reporting a vulnerability

**Do not report vulnerabilities through public GitHub issues, discussions or pull requests.**

Report them privately, by either:

* GitHub: **Security → Report a vulnerability** on
  [openFHIR/openfhir](https://github.com/openFHIR/openfhir/security/advisories/new), or
* email: [security@open-fhir.com](mailto:security@open-fhir.com).

Please include:

* the affected version(s) (`X.Y.Z`, or the Docker image tag/digest);
* the affected component or endpoint and the configuration needed to reproduce it;
* steps to reproduce, or a proof of concept;
* the impact as you understand it (what an attacker can read, change or disrupt);
* whether the vulnerability is known to be actively exploited;
* how you would like to be credited in the advisory, if at all.

## Response targets

| Step                                   | Target                                                                 |
|----------------------------------------|------------------------------------------------------------------------|
| Acknowledge the report                 | 3 working days                                                         |
| Assess it (confirmed or not, severity) | 7 working days                                                         |
| Fix critical / high severity           | a patch release as soon as possible                                    |
| Fix medium / low severity              | the next scheduled release                                             |

We keep you informed of progress and agree on a disclosure date with you.

## Release policy

openFHIR follows [Semantic Versioning](https://semver.org/):

| Change                  | Release                                                                                   |
|-------------------------|-------------------------------------------------------------------------------------------|
| Security fix            | `X.Y.Z+1`, containing **only** the fix, built from the last release of the line `X.Y`     |
| Bug fix                 | `X.Y.Z+1`, containing only fixes                                                          |
| New features            | `X.Y.0`                                                                                   |
| Breaking changes        | `X.0.0`                                                                                   |

Patch releases are **rolling-upgradable**: they contain no database migrations and no changes to the messages nodes of
a cluster exchange, so the nodes of a cluster can be upgraded one at a time.

### Docker images

| Tag                        | Points at                                                        |
|----------------------------|------------------------------------------------------------------|
| `openfhir/openfhir:X.Y.Z`  | that release, never moves                                        |
| `openfhir/openfhir:X.Y`    | the newest patch release of line `X.Y`                           |
| `openfhir/openfhir:latest` | the newest release overall; moves to the next minor/major too    |
| `openfhir/openfhir:build`  | the development branch; a preview, not a release                 |

Use `:X.Y` to receive patch releases (and with them security fixes) without picking up features, or `:X.Y.Z` to pin
a release exactly. Every release image carries an SBOM and a provenance attestation:

```bash
docker buildx imagetools inspect openfhir/openfhir:X.Y.Z --format '{{json .SBOM}}'
docker buildx imagetools inspect openfhir/openfhir:X.Y.Z --format '{{json .Provenance}}'
```

The GitHub Release of each version also has the CycloneDX SBOM of the Maven build attached (`bom.json`).

This policy applies from 3.1.0 on. Earlier patch releases (up to 3.0.3) were cut from the development branch and could
contain features.

## Disclosure

Every fixed vulnerability gets a [GitHub Security Advisory](https://github.com/openFHIR/openfhir/security/advisories),
with a CVE requested through GitHub, published together with the patch release that fixes it. The release's
`CHANGELOG.md` section lists the fix under `### Security`. 
