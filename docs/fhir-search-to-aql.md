# FHIR Search to AQL Translation

This document describes how OpenFHIR translates a FHIR search URL into one or more openEHR AQL queries.

## Entry point

The process starts with a `ToAqlRequest` which carries:
- `fhirFullUrl` — the full FHIR search URL, e.g. `Observation?code=29463-7&status=final`
- `template` — optional openEHR template ID to restrict the search to a specific template
- `ehrId` — the EHR ID placeholder that ends up in the generated AQL

The request is handled by `ToAql.toAql()`.

---

## Step 1 — Parse the URL

The URL is parsed into two parts:
- **Resource type** — extracted from the path segment before `?`, e.g. `Observation`
- **Query params** — each `key=value` pair becomes a `FhirQueryParam(name, value, handled)`

The `handled` flag is used to track which params have been consumed by the translation process (see below).

The special param `_profile` is extracted and removed from the param list before further processing — it is used to narrow context lookup, not as a search condition.

---

## Step 2 — Find relevant contexts

A **FhirConnect context** (`FhirConnectContextEntity`) ties an openEHR template to a FhirConnect mapping file. It defines which FHIR resource type is handled, which openEHR template it maps to, and optionally which FHIR profile URL it applies to.

Context lookup follows this priority:

1. If `template` was provided in the request → look up context by template ID directly
2. If `_profile` was provided → find the context whose profile URL matches
3. If neither → load all contexts for the current tenant

If a template or profile was explicitly provided but no matching context exists, a `400 Bad Request` is thrown.

---

## Step 3 — Narrow by resource type and preconditions

Each context has one or more **model mappers** (`OpenFhirFhirConnectModelMapper`), each of which declares which FHIR resource type it handles (`fhirConfig.resource`). The narrowing step keeps only those model mappers where:

1. `fhirConfig.resource` matches the parsed resource type
2. **Preconditions pass** (see below)

The narrowing also recurses into **slot archetypes** — if a mapping references another archetype via `slotArchetype`, that archetype's mappers are also evaluated. Infinite recursion is prevented by tracking which slot was followed.

### Preconditions

A FhirConnect model mapper may declare `preprocessor.fhirConditions` — a list of conditions that must be satisfied by the incoming query params for the mapper to be relevant. Each condition has:
- `targetAttribute` — the FHIR attribute path (e.g. `code`)
- `operator` — `one of` or `not of`
- `criterias` — list of allowed/disallowed values

During precondition evaluation, the condition's `targetAttribute` is resolved to a full FHIR path (e.g. `Observation.code`) using HAPI FHIR's `@SearchParamDefinition` reflection (see Step 4). If a query param maps to that path:
- `one of`: the mapper is only relevant if the param value is in the criteria list
- `not of`: the mapper is excluded if the param value is in the criteria list

If the relevant query param is not present at all and the condition is `one of`, the mapper is considered not relevant. When a precondition consumes a param (e.g. a `one of` condition matches), that param is marked `handled=true` so it is not treated as an unhandled param later.

---

## Step 4 — Construct MappingHelpers

For each remaining context, `HelpersCreator` walks the FhirConnect model mappers and the openEHR web template to build a tree of `MappingHelper` objects. Each `MappingHelper` represents a concrete mapping from a FHIR path to an openEHR path and captures:

- `fullFhirPath` — the full FHIR element path, e.g. `Observation.value`
- `fullOpenEhrPath` — the full openEHR path including archetype, e.g. `openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]`
- `archetype` — the openEHR archetype ID extracted from the path
- `detectedType` — the openEHR RM type of the data point (e.g. `DV_QUANTITY`, `DV_CODED_TEXT`)
- `children` — nested helpers for sub-elements

---

## Step 5 — Match query params to MappingHelpers

Each query param is resolved to a FHIR path using HAPI FHIR's `@SearchParamDefinition` annotations on the R4 resource class. For example, the param name `code` on `Observation` resolves to `Observation.code`.

The resolved path is matched against the `fullFhirPath` of every `MappingHelper`. The matching supports:
- **Substring containment** — the FHIR path segment contains (or is contained by) the helper's path, to handle both more-specific and less-specific paths
- **Pipe-separated paths** — FHIR search param paths can be multi-valued (e.g. `(Observation.value as Quantity) | (Observation.value as SampledData)`); each segment is checked independently
- **Archetype-only helpers** are skipped (helpers where `fullFhirPath` equals the generating resource type, i.e. no specific attribute mapping)

If no helper matches a query param and that param was not already marked `handled`, it is added to `ToAqlResponse.unhandledParams` with type `ERROR`.

---

## Step 6 — Build AQL

### No query params

If no query params were provided, an **archetype-only AQL** is generated per context — a bare `SELECT ... FROM EHR e CONTAINS ... WHERE e/ehr_id/value='{{ehrid}}'` with no WHERE conditions beyond the EHR ID. If `narrowToTemplate` is true, the composition archetype is included in the CONTAINS clause.

### With query params

Matched helpers are grouped by context. For each context, all matched helpers and their param values are combined into **one entry AQL and one composition AQL**, with each additional condition AND-ed onto the same query.

**Entry AQL** (targets a specific openEHR entry archetype):
```
SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2]
WHERE e/ehr_id/value='{{ehrid}}'
AND h/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value = 80
AND h/data[at0002]/events[at0003]/data[at0001]/items[at0005]/value = 'final'
```

**Composition AQL** (targets the composition level):
```
SELECT c FROM EHR e CONTAINS COMPOSITION c
WHERE e/ehr_id/value='{{ehrid}}'
AND c/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/.../value = 80
AND c/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/.../value = 'final'
```

If `narrowToTemplate=true` (set when either `template` or `_profile` was provided in the request), the composition archetype is added to the composition AQL:
```
SELECT c FROM EHR e CONTAINS COMPOSITION c [openEHR-EHR-COMPOSITION.growth_chart.v0]
CONTAINS OBSERVATION [openEHR-EHR-OBSERVATION.body_weight.v2]
WHERE e/ehr_id/value='{{ehrid}}'
```

### Data type-aware value formatting

The value in the WHERE clause is formatted based on the `detectedType` of the helper:
- `DV_QUANTITY` — unquoted numeric value: `/value = 80`
- All other types — quoted string value: `/value = '29463-7'`

### AQL validation

Each generated AQL is parsed by the EHRbase AQL parser before being included in the response. If parsing fails, the AQL is silently dropped and logged as an error.

---

## Response

`ToAqlResponse` contains:
- `aqls` — list of `AqlResponse(aql, type)` where type is `ENTRY` or `COMPOSITION`
- `unhandledParams` — list of `UnhandledParam(paramName, type, message)` for params that could not be mapped; currently always `ERROR` type

The `{{ehrid}}` placeholder in the generated AQL is intended to be replaced by the caller with the actual EHR ID at query time.
