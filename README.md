[![openFHIR Logo](openfhir-logo.png)](openfhir-logo.png)

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**openFHIR** is an open-source engine for healthcare interoperability that enables **bidirectional mappings between
openEHR and HL7 FHIR**, based on the **[FHIR Connect specification](https://github.com/SevKohler/FHIRconnect-spec)**.

openFHIR translates openEHR Compositions and FHIR Resources without storing clinical data itself — making it a flexible
component for modern health IT architectures.

> **Note:** This open-source version is **not intended for production use**. It lacks critical production capabilities
> including **authentication and role-based access control**, **terminology server integration**, and **performance
optimizations**. For production-ready deployments with full security, scalability, and enterprise support, please refer
> to the **[Enterprise version](https://open-fhir.com)**.
>
> ### Open Source vs Enterprise
>
> | Feature                                                        | Open Source | Enterprise |
> |----------------------------------------------------------------|:-----------:|:----------:|
> | Bi-directional mapping between openEHR and FHIR                | ✅ | ✅ |
> | Compliance with FHIRConnect specification                      | ✅ | ✅ |
> | Included support and consultancy                               | No | ✅ |
> | Postgres support                                               | No | ✅ |
> | Terminology server integration                                 | No | ✅ |
> | Operational Templates sync with underlying openEHR Server      | No | ✅ |
> | Multitenancy                                                   | No | ✅ |
> | Mapping insights support                                       | No | ✅ |
> | Authentication and role-based access control                   | No | ✅ |
> | Integration with a git repository as a source of mapping files | No | ✅ |
> | Optimized performance                                          | No | ✅ |
> | Support for High Availability setups                            | No | ✅ |

----

## Changelog

Please check the [CHANGELOG](https://github.com/openfhir/openfhir/blob/develop/CHANGELOG.md)

## Documentation

Check out the documentation at https://open-fhir.com/documentation

## Try openFHIR Online

If you’d like to explore openFHIR without installing anything locally, you can use the public sandbox environment, which
always runs the latest release of openFHIR:

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

```yaml
docker run -e SPRING_DATA_MONGODB_URI=mongodb://your-mongodb-url \
-p 8080:8080 openfhir/openfhir:latest
```

Additionally, check out the [Docker Compose](https://github.com/openfhir/openfhir/blob/develop/docker-compose.yml) file
for a complete setup with mongodb.

---

## Credits

Original openFHIR project was initially open-sourced by MedBlocks in https://github.com/medblocks/openFHIR. Credits:

- **[Medblocks](https://medblocks.com/)**
- **[Karkinos Healthcare](https://karkinos.in/)**
- **[HiGHmed](https://www.highmed.org/)**
- **[Syntaric](https://syntaric.com/)** 
