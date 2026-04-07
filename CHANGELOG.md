# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

---
## [Unreleased]

## [2.0.6] - 2026-04-07
### Added
- MappingHelper to the `PrePostFhirInstancePopulator` method signature
- Added ability to collect metrics for mapping executions (see `MappingMetricsLogger`)
- `*Manager` as a proxy class inbetween consumers and transactional `*Services`

### Fixed
- criterias are properly evaluated when multiple (previously only 0th criteria was evaluated)
- preprocessor fhircondition no longer results in a mapping going openehr->fhir [#35](https://github.com/openFHIR/openfhir/issues/35)                                                                                                                                                                                                            

### Changed
- `bootstrap.recursively-open-directories` now defaults to true, meaning openfhir engine will go through all directories and subdirectories of the bootstrap location to find mappings and contexts

## [2.0.5] - 2026-03-23

### Added
- DV_TEXT can implicitly be mapped to/from DV_CODED_TEXT

## [2.0.4] - 2026-03-23

### Fixed
- when mapping to FHIR Enumeration that's a List (like AllergyIntolerance.category), this is now properly mapped and serialized (previously HAPI serialization was throwing errors)
- criterias are properly evaluated when multiple (previously only 0th criteria was evaluated)

### Added
- IPS tests
- CodedText <> Enumeration mapping
- Additional sections to IPS mappings and tests on the codebase

### Changed
- interface on `ToFhirPrePostProcessorInterface.postProcess` now includes also the context, opt and compositions

## [2.0.3] - 2026-03-20
### Fixed
- manual mappings may produce duplicate results due to incorrect manual mapping construction
- fhirpath with fhirconditions was in some cases wrongly constructed, resulting in missin mappings
- $reference can be suffix with further AQL path when necessary
- AQL generation now fallbacks to archetype-only AQL when no param matches
- logging when something goes wrong in toAql now works (previously stacktrace was not logged)

### Added
- DV_TEXT maps to CodeableConcept.text
- ability to transform discrete ContentItems on the fly
- ehrid is now replaced with the ehrid coming in the request in toAql translation

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
