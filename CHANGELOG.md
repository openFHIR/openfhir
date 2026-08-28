# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## Unreleased
### Security
- dependency bumps resolving all 12 HIGH CVEs from the Trivy scan (issue #182), but taken to the newest
  currently available versions:
  - Spring Boot 3.3.2 → 3.5.16 (pulls Tomcat 10.1.55 — CVE-2025-48988/-48989/-55752; Spring Framework
    6.2.19 — CVE-2025-22235/-41249, CVE-2024-38816/-38819; json-smart 2.5.2 — CVE-2024-57699)
  - HAPI FHIR 7.2.1 → 8.12.0 (pulls org.hl7.fhir.core 6.9.12 — CVE-2024-45294/-51132/-52007)
  - ucum 1.0.9 → 1.0.10 (CVE-2024-55887 was already fixed at 1.0.9; taken to latest)
  - archie 3.11.0 → 3.13.0 and springdoc 2.1.0 → 2.8.17, required for compatibility with the above
  - jackson-bom pinned to 2.19.4: jackson-databind 2.20+ removed the deprecated
    `PropertyNamingStrategy` constants Archie still uses (2.19.4 is the pairing the ehrbase SDK
    line itself ships with archie 3.13)
- HAPI 8 adaptations: `Narrative.div` instantiation resolves the field's own `XhtmlNode` type again
  (HAPI 8 annotates the child with the un-settable `XhtmlType` pseudo-type), and expected narrative
  fixtures updated for HAPI 8's XHTML composer serializing empty elements as `<td/>`/`<span/>`

### Added
- toFHIR: a `DV_DURATION` can now populate a FHIR `Duration`, converting the ISO 8601 string to a value
  plus a UCUM time code

### Fixed
- toOpenEHR: a FHIR `Duration` no longer loses its unit on the way into a `DV_DURATION` or a `DV_TEXT`
- toOpenEHR: a `DV_IDENTIFIER`'s `|type` and `|assigner` no longer come back with a placeholder system
  prepended, so a `|type` of `Prescription number` no longer returns as
  `http://openehr.org/identifier/type::Prescription number`. `IdentifierParser` invents those
  placeholder systems on the way out for parts that carry no system of their own; the return leg
  re-emitted them as `system::value` instead of stripping them, as it already did for `|issuer`. A
  system that genuinely came from the source FHIR is unaffected.
- openehrCondition targetAttribute now correctly evaluates empty/not empty even when pin pointing a non-ending path
- when openehrCondition targetAttribute references recurring element, this is now correctly evaluated in condition
- toFHIR: FHIR Path casts to primitive types are now properly evaluated (as Integer -> IntegerType, etc.)
- a fhir path with no cast at all no longer throws a `NullPointerException` when its cast type is looked up.
- toOpenEHR: a `DV_PROPORTION`'s `|type` is no longer hardcoded to `2` (percent). FHIR has no field naming the
  kind of proportion, so it is derived from the denominator: 100 gives `2`, 1 gives `1`, an otherwise integral
  pair gives `4`, anything else `0`. A fraction (`3`) is never inferred, as a bare `Quantity` cannot distinguish
  it from an integer fraction. `|type` is now written only where a `|denominator` was, so a non-percent
  `Quantity` no longer claims to be a percent with no denominator at all — an invalid `DV_PROPORTION` a strict
  server may reject. A percent is recognised from either the UCUM code or the unit, so a device sending a bare
  `97 %` maps correctly with nothing else to go on.
- toFHIR: a `DV_PROPORTION` that is not a percent no longer loses its denominator. Only a percent has a faithful
  FHIR representation (UCUM `%`); every other kind was reduced to its numerator, so a 3/4 ratio arrived as a bare
  `3`. The denominator is now carried on a `proportion-denominator` extension and the openEHR `|type` on a
  `proportion-kind` extension, which is what lets kinds the denominator alone cannot re-derive survive a round
  trip. The return leg prefers a carried `|type` over deriving one.

### Changed
- **fhirConditions are no longer compiled into the FHIRPath string** (`Observation.component.where(...)`-style
  splicing). Plain mapping paths are evaluated as-is and conditions are applied programmatically
- `Condition` is now plural-only: the deprecated singular `targetAttribute`/`criteria` fields were removed from
  the model. YAML/JSON mappings using the singular keys keep working — they are normalized into
  `targetAttributes`/`criterias` at deserialization time.
- all `targetAttributes` of a fhirCondition are now evaluated with the OR-implied semantics the schema documents
  (previously only the first attribute was baked into the where clause), and a `type` condition compares against
  all `criterias` (previously only the first)
- a fhirCondition without `targetAttributes` (e.g. a bare `type` condition carrying only targetRoot and
  criteria) is never treated as path-filtering — it only feeds the type gate
- **date/time values now keep their source's timezone offset in both directions, and no longer gain
  one that was not there.** Offsets are preserved exactly as written: `Z` stays `Z`, `+00:00` stays
  `+00:00`, `+01:00` keeps its wall-clock reading.
- `BootstrapService` extension points widened so a distribution can add file types of its own to the bootstrap
  scan instead of running a parallel one: `classify`, `apply`, `resolveExisting`, `saveLedgerEntry` and
  `relativePath` are now `protected`, and `FileType` is public. A subclass can override `classify` to recognise a
  new suffix and `apply` to route that one type elsewhere, delegating every other type to `super` and inheriting
  the directory walk, hash comparison, ledger and summary unchanged. No behaviour change here — no bodies moved,
  and with no subclass on the classpath nothing dispatches differently.
- `FileType` gained a `CONCEPTMAP` constant, so the ledger's `entityType` and the `BootstrapSummary` breakdown
  share one vocabulary across distributions. It is inert in this project: `classify` never returns it, and the
  `upsert` arm it forces is unreachable.


## [2.2.5] - 2026-08-17
### Added
- KDS v1.0 mappings in unit tests
- support for PARTY_PROXY (from Reference and Idenifier)
- `unidirectional` can now also be declared in the header (`spec`) of a mapping file, in which case
  the whole file is treated as unidirectional and is skipped when mapping in the opposite direction.
  A `unidirectional` on an individual mapping still takes precedence, so the header acts as the
  default for mappings that don't declare one. (#98)
- `POST /$bootstrap` — re-runs the bootstrap directory scan without a restart, returning a JSON summary
  (`created`, `updated`, `unchanged`, `failed` and a per-file breakdown). Returns `409` if a run is already in
  progress.
- `GET /bootstrap` — lists the bootstrap ledger of the logged-in user: which files have been bootstrapped, from
  which path, and which entity each one created.
- bootstrapped files are now re-applied when their content changes. Each bootstrap ledger entry records a SHA-256
  hash of the file content and the id of the entity it created, so a re-run (on startup or via `POST /$bootstrap`)
  creates new files, updates changed ones **in place under the same entity id**, and skips unchanged ones. Files
  that were bootstrapped before but are no longer on disk are reported with a warning; their entities are left
  untouched. Ledger rows written by earlier versions have no hash and are treated as changed, which re-applies
  them once and backfills the new fields without creating duplicate entities.
### Changed
- bootstrap ledger entries are now keyed by the file's path relative to the bootstrap dir instead of its bare
  filename, so identically named files in different sub-folders no longer collide, and they are written with the
  tenant they were created under.
- `BootstrapEntity` gained four fields: `path`, `contentHash`, `entityId` and `entityType`, and now extends
  `UserBasedEntity` like the other entities

### Fixed
- a sub-directory whose name ended in `.yaml`/`.yml`/`.opt` was processed as if it were a mapping file after being
  recursed into
- toFHIR: `dosage.doseAndRate` Range values are no longer dropped, so both a dose Range
  (`DV_INTERVAL<DV_QUANTITY>` in `Dosis`) and a rate Range now survive the roundtrip instead of
  producing a dosage with only `route`. Three gaps contributed: a `Range` had no branch in the FHIR
  instance populator and so was silently discarded; programmed mappings never set the detected RM
  type, so a `type` openEHR condition could not tell an interval-valued element from a
  quantity-valued one; and the dosage custom mappings were handed the unresolved template path
  (with `[n]` placeholders) instead of the composition's concrete flat keys, so they found no
  values to read. (#94)
- toFHIR: values no longer leak between FHIR list entries when the same slot archetype is instantiated
  more than once. The openEHR occurrence index of a data point (the `0` in `prefix:0`) addresses a
  position inside the source cluster, not a position in the FHIR list it is being written to, and was
  being used as the latter. With two instances of the same slot feeding one list — e.g. a Patient's
  `name` receiving both an official name and a maiden name from two `CLUSTER.structured_name.v1`
  clusters — the second mapping's index restarted at 0 and overwrote the first entry, so the official
  name got `use: maiden` and the maiden name lost its `use` entirely. Each entry now binds to the
  element its own mapping is building. (#90)
- toOpenEHR: a minute-denominated infusion `rateRatio` (e.g. 1500 mg / 30 min) is now stored as a
  `PT30M` administration duration instead of `PT30H`. A DV_DURATION can only be written to the flat
  format through its `|day`/`|hour`/`|minute`/`|second` components, and the ehrbase SDK (2.19.0)
  unmarshaller builds the value as `ofHours(hour) + ofHours(minute) + ofHours(second)`, inflating
  every sub-hour component to hours. The decoded duration is now corrected against the components
  that were actually written. Hour- and day-denominated rates were never affected. (#92)
- Non-daily dosage schedules are now reconstructed as `timing.repeat` when mapping to FHIR. Previously the period stored in the openEHR `timing_nondaily.v1` cluster was dropped, leaving the schedule only as prose in `dosage.text` (#95)
- KDS mappings (test suite) for #93
- toFHIR: an openEHR `null_flavour` on an ELEMENT is now reconstructed as a FHIR
  `data-absent-reason` extension on the FHIR primitive it maps to, so it is serialized as the
  primitive's sibling element (`_city`, `_line`, …) instead of being dropped. openEHR null flavours
  are translated to their FHIR equivalents (`masked`/272 → `masked`, `unknown`/253 and
  `no information`/271 → `unknown`, `not applicable`/273 → `not-applicable`). This is generic
  plumbing and requires no per-field mapping expression. Complex datatypes are left untouched,
  since elements such as `Observation.value[x]` carry a dedicated sibling `dataAbsentReason`
  element instead. (#91)
- any condition not amended against a web template threw an NPE, now fixed

## [2.2.4] - 2026-06-16

### Fixed
- AQL generation proper paths for CodedText, CodePhrase
### Added
- ability to specify hardcoded AQLs for specific contexts via `_query` in context mapping files. `_query` is a list of
  entries, each with an `aql` string and a `rules` list. When a toAQL request matches any rule in an entry, the
  hardcoded `aql` is returned directly instead of generating AQL dynamically. Rules are matched against the incoming
  FHIR request as follows:
  - **Operation** (e.g. `$summary`) — matches if the parsed URL contains that operation (e.g. `Patient/123/$summary`)
  - **Resource with query params** (e.g. `Observation?category=height`) — matches if the resource type equals the rule
    resource and all key-value pairs in the rule are present in the incoming query params (in any order; the request
    may have additional params beyond what the rule specifies)
  - **Resource only** (e.g. `Condition`) — matches any request for that resource type regardless of params

  Example:
  ```yaml
  _query:
    - aql: "SELECT c FROM EHR e[ehr_id/value='{{ehrid}}'] CONTAINS COMPOSITION c WHERE c/archetype_details/template_id/value='something'"
      rules:
        - "$summary"
    - aql: "SELECT c FROM EHR e[ehr_id/value='{{ehrid}}'] CONTAINS COMPOSITION c WHERE c/archetype_details/template_id/value='something-else'"
      rules:
        - "Observation?category=height"
  ```
  - ordinal mappings
### Changed
- certain deprecated fields in model mappings are now left out from serialization (fhirCondition, criteria, ..)

## [2.2.3] - 2026-05-23

### Fixed

- `DateType` mapping now added (when FHIR field type is https://hl7.org/fhir/R4/datatypes.html#date)
- context mapper is now properly found also by incoming Bundle.meta.profile, if incoming Resource if of type Bundle (previously one of Bundle.entries had to have a matching profile)
- STU3 ofType regression, because this function doesn't exist so certain STU3 fhirConditions weren't evaluated
- mapping case when you would map only an extension to a 'code' primitive where that 'code' was a recurring element (i.e. AllergyIntolerance.category)

### Added

- `other_participations` mapping (`perfomer` from/to Reference, `function` from/to DV_TEXT)
- New `feederAudit` custom mapping code: when referenced in a model mapping, automatically serializes data point
  referenced by `with.fhir` and places it in `feeder_audit`. Formalism is set to `application/fhir+json`.
  OriginatingSystemAudit.systemId can be passed in as first argument of the mappingCode (i.e.
  `feederAudit(system-of-mine)`). This is an optional parameter, however if it's not passed in, a value of `openFHIR`
  will be hardcoded, as it's a required field in openEHR RM.
  See [blood-pressure.model.yml](core/src/test/resources/blood_pressure/blood-pressure.model.yml)
  and [blood-pressure-parent.model.yml](core/src/test/resources/blood_pressure/blood-pressure-parent.model.yml) for
  examples.
- added `_bundleMetadata` in context mappings. By default, openFHIR always produces a Bundle of type `collection`
  without any
  additional metadata information on the Bundle (unless specified with model mappings when hierarchy allows that). With
  `_bundleMetadata`, you can now override this behavior by specifying `type`, `profile`, `identifier_system` and
  `identifier_value` (if identifier_value is not defined, but identifier_system is, value of the identifier will be
  autogenerated UUID). `_bundleMetadata.profile` is not to be mistaken with `context.profile.url`, which is needed to
  evaluated for which incoming FHIR Resources this specific context is applicable
- tests for certain EPS sections (mappings from [freshEHR](https://github.com/freshehrteam/EHDS/tree/main/Mappings))
- ability to hardcode leaf types (i.e. |function)

### Changed

## [2.2.2] - 2026-05-19

### Fixed

- ad-hoc Composition generation when a section was given a name and ended up with AQL path name/value=''
- fhirCondition 'one of' and 'not of' now properly evaluated even if subpath matches
- `spec.fhirConfig.structureDefinition` in model mappings now implicitly asserts that incoming IBase is of same type (
  going FHIR->openEHR) and skips a mapping if it's not
- `coded_text_value` leaf type now added when multiple options are possible on a field
- if a mapping only has manualMappings as children, possible-rm-types should be propagated to the children mappings

### Added

- tests for IPS Medical Devices section (unverified mappings)
- added a docker hub build action that builds arm64 compliant docker image
- IPS post-processing logic, which makes sure Bundle produced is of type `document` and also adds Bundle profile and
  required Bundle identifier
- support for `DV_PARSABLE` (at the moment, it always assumes |formalism of text/html)
- narrative generation templates for medical devices and procedures

### Changed

- engine now by default moves contained Resources to separate Bundle entries (can be changed by setting
  `openfhir.contained-to-separate-entities` to `false`)
- fhirCondition operator 'type' can now be used also as filtering rather than just conditioning the whole mapping (
  unless a mapping has no children, then it's only excluding)
- when adding coded text manual mappings and a field type is `TEXT`, it will be populated properly as coded text

## [2.2.1] - 2026-05-04

### Changed

### Added

- `generateNarrative` programmed mapping now accepts an optional second argument specifying a profile URL, e.g.
  `generateNarrative(entry, http://hl7.org/fhir/StructureDefinition/Condition)`

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
    - behavior when a mappins is only a manual mapping, in which case a NONE is now
      implied [#54](https://github.com/openFHIR/openfhir/issues/54)

### Added

- Added mongo indexes to optimize performance
- memory optimizations
- `DELETE /opt/{id}` — delete an Operational Template by ID
- `DELETE /fc/model/{id}` — delete a FHIR Connect model mapper by ID
- `DELETE /fc/context/{id}` — delete a FHIR Connect context mapper by ID
- Creating a new release now triggers a workflow publishing Maven packages to GitHub Packages
- `link` is now a supported keyword in model mapping (although not yet implemented, but mapping creation won't fail if
  it contains this option)
- KDS mappings and tests amended to support FhirConnect release1 of the library
- Additional openEHR Data Types for AQL mappings
- Support for different FHIR versions: STU3, R4 (was supported before), R4B, R5
- mapping of `|other`
- New `generateNarrative` custom mapping code: when referenced in a model mapping, automatically generates a FHIR
  narrative (`text`) on the resource being built during openEHR→FHIR mapping using HAPI's built-in Thymeleaf narrative
  generator

### Fixed

- `GET /opt/{id}`, `GET /fc/model/{id}`, `GET /fc/context/{id}` now return 404 instead of 200 with empty body when the
  resource is not found
- search of opt by templateId now filters properly (before it returned all)
- `IParser` is now created per-call instead of shared as a singleton, fixing a potential thread-safety issue under
  concurrent requests
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
- preprocessor fhircondition no longer results in a mapping going openehr->
  fhir [#35](https://github.com/openFHIR/openfhir/issues/35)

### Changed

- `bootstrap.recursively-open-directories` now defaults to true, meaning openfhir engine will go through all directories
  and subdirectories of the bootstrap location to find mappings and contexts

## [2.0.5] - 2026-03-23

### Added

- DV_TEXT can implicitly be mapped to/from DV_CODED_TEXT

## [2.0.4] - 2026-03-23

### Fixed

- when mapping to FHIR Enumeration that's a List (like AllergyIntolerance.category), this is now properly mapped and
  serialized (previously HAPI serialization was throwing errors)
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
- fixed DV_TEXT (String) to CodeableConcept mapping (now maps to CodeableConcept.text, before it didn't map at
  all) [issue#13](https://github.com/openFHIR/openfhir/issues/13)
- when there is more than 1 possible rmType, engine now correctly finds the right one (when openEHR -> FHIR, this is
  done by deducing rmType based on the data; when going FHIR->openEHR it is based on FHIR
  type) [issue#14](https://github.com/openFHIR/openfhir/issues/14)

## [2.0.0] - 2026-03-01

Major rewrite of openFHIR, incorporating features from the former commercial version and the open-sourced
medblocks/openfhir project, which has now been deprecated in favor of this repository.
