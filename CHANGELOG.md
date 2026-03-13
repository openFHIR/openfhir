# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## Unreleased

### Added
- BETA feature of translation of FHIR Search to AQL ([fhir-search-to-aql.md](docs/fhir-search-to-aql.md))

### Changed

### Fixed
- when a duplicate OPT is trying to be created, server now responds with 400 not 500

## [2.0.0] - 2026-03-01
Major rewrite of openFHIR, incorporating features from the former commercial version and the open-sourced medblocks/openfhir project, which has now been deprecated in favor of this repository.