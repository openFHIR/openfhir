
[![openFHIR Logo](openfhir-logo.png)](openfhir-logo.png)

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**openFHIR** is an open-source engine for healthcare interoperability that enables **bidirectional mappings between openEHR and HL7 FHIR**, based on the **[FHIR Connect specification](https://github.com/SevKohler/FHIRconnect-spec)**.

openFHIR translates openEHR Compositions and FHIR Resources without storing clinical data itself — making it a flexible component for modern health IT architectures.

----

## Changelog

Please check the [CHANGELOG](https://github.com/openfhir/openfhir/blob/develop/CHANGELOG.md)

## Documentation

Check out the documentation at https://open-fhir.com/documentation

## Try openFHIR Online

If you’d like to explore openFHIR without installing anything locally, you can use the public sandbox environment, which always runs the latest release of openFHIR:

**https://sandbox.open-fhir.com/**

The sandbox provides a live instance of the openFHIR engine where you can:

- Test FHIR ↔ openEHR transformations
- Validate mapping configurations
- Experiment with API requests
- Explore mapping transformation with a UI called [openFHIR Atlas](https://open-fhir.com/documentation/1.2.9/atlas.html)

> ⚠️ The sandbox is intended for testing and evaluation purposes only.  

---

## Get Started Locally (Docker)

For local development or testing we recommend running openFHIR using Docker.
