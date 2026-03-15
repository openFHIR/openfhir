# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

---
## Unreleased

## [2.0.2] - 2026-03-15

### Added
- tests for toAql translation
- abbility to translate separate ContentItems not necessarily the whole Composition

### Fixed
- toAql now properly exposed via RESTful API (/openfhir/toaql), but still a BETA feature

## [2.0.1] - 2026-03-14

### Added
- BETA feature of translation of FHIR Search to AQL ([fhir-search-to-aql.md](docs/fhir-search-to-aql.md))

### Changed
- removed PreAuthorize from openfhir controller (although it didn't have any functionality before either)

### Fixed
- when a duplicate OPT is trying to be created, server now responds with 400 not 500
- fixed DV_TEXT (String) to CodeableConcept mapping (now maps to CodeableConcept.text, before it didn't map at all) [issue#13](https://github.com/openFHIR/openfhir/issues/13)
- when there is more than 1 possible rmType, engine now correctly finds the right one (when openEHR -> FHIR, this is done by deducing rmType based on the data; when going FHIR->openEHR it is based on FHIR type) [issue#14](https://github.com/openFHIR/openfhir/issues/14)

## [2.0.0] - 2026-03-01
Major rewrite of openFHIR, incorporating features from the former commercial version and the open-sourced medblocks/openfhir project, which has now been deprecated in favor of this repository.
