# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

---
## Unreleased
### Fixed
- ad-hoc Composition generation when a section was given a name and ended up with AQL path name/value=''
- fhirCondition 'one of' and 'not of' now properly evaluated even if subpath matches
- `spec.fhirConfig.structureDefinition` in model mappings now implicitly asserts that incoming IBase is of same type (going FHIR->openEHR) and skips a mapping if it's not

### Added
- tests for IPS Medical Devices section (unverified mappings)
- added a docker hub build action that builds arm64 compliant docker image
- IPS post-processing logic, which makes sure Bundle produced is of type `document` and also adds Bundle profile and required Bundle identifier

### Changed
- engine now by default moves contained Resources to separate Bundle entries (can be changed by setting `openfhir.contained-to-separate-entities` to `false`)
- fhirCondition operator 'type' can now be used also as filtering rather than just conditioning the whole mapping (unless a mapping has no children, then it's only excluding)

## [2.2.1] - 2026-05-04

### Changed
### Added
- `generateNarrative` programmed mapping now accepts an optional second argument specifying a profile URL, e.g. `generateNarrative(entry, http://hl7.org/fhir/StructureDefinition/Condition)`
### Fixed
- templates for narrative generation are now properly overridable by providing own template
- condition and allergy narrative templates now correctly filter bundle entries by resource type
- narrative generation no longer fails with `NoSuchMethodException` when iterating bundle entries

## [2.2.0] - 2026-05-02

### Changed
- All openfhir-specific configuration properties now use the `openfhir.` prefix for consistency:
  - `bootstrap.dir` → `openfhir.bootstrap.dir`
  - `bootstrap.recursively-open-directories` → `openfhir.bootstrap.recursively-open-directories`
  - `db.type` → `openfhir.db.type`
  - behavior when a mappins is only a manual mapping, in which case a NONE is now implied [#54](https://github.com/openFHIR/openfhir/issues/54)

### Added
- Added mongo indexes to optimize performance
- memory optimizations
- `DELETE /opt/{id}` — delete an Operational Template by ID
- `DELETE /fc/model/{id}` — delete a FHIR Connect model mapper by ID
- `DELETE /fc/context/{id}` — delete a FHIR Connect context mapper by ID
- Creating a new release now triggers a workflow publishing Maven packages to GitHub Packages
- `link` is now a supported keyword in model mapping (although not yet implemented, but mapping creation won't fail if it contains this option)
- KDS mappings and tests amended to support FhirConnect release1 of the library 
- Additional openEHR Data Types for AQL mappings
- Support for different FHIR versions: STU3, R4 (was supported before), R4B, R5
- mapping of `|other`
- New `generateNarrative` custom mapping code: when referenced in a model mapping, automatically generates a FHIR narrative (`text`) on the resource being built during openEHR→FHIR mapping using HAPI's built-in Thymeleaf narrative generator

### Fixed
- `GET /opt/{id}`, `GET /fc/model/{id}`, `GET /fc/context/{id}` now return 404 instead of 200 with empty body when the resource is not found
- search of opt by templateId now filters properly (before it returned all)
- `IParser` is now created per-call instead of shared as a singleton, fixing a potential thread-safety issue under concurrent requests
- when a `manual` mapping has a `fhirCondition`with `$fhirRoot`, this is now correctly evaluated
- fhircondition `type` is now properly evaluated even when fhirPath has a resolve()
- fhircondition `type` is now properly evaluated even when Resources are nested in a Bundle
- when followed-by mapping is referencing a `$resource`, it is now correctly evaluated


## [2.1.0] - 2026-04-07
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
