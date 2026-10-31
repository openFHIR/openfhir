# FHIR Search to AQL Translation

This document describes how OpenFHIR translates a FHIR search URL into one or more openEHR AQL queries.

## Entry point

The process starts with a `ToAqlRequest` which carries:
- `fhirFullUrl` — the full FHIR search URL, e.g. `Observation?code=29463-7&status=final`
- `template` — optional openEHR template ID to restrict the search to a specific template
- `ehrId` — optional; the patient's EHR id, filled into the `{{ehrid}}` placeholder of the generated AQL
- `subject` — optional, instead of `ehrId`; the patient as the `EHR_STATUS.subject` of their EHR: `id` and optional
  `namespace` (see [Patient scope](#patient-scope))

The request is handled by `OpenFhirEngine.toAql()`: it checks the patient (`AqlPatientScope.validate`), has
`ToAql.toAql()` generate the AQLs, and scopes them to the patient (`AqlPatientScope.apply`).

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
AND h/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value/magnitude = 80
AND h/data[at0002]/events[at0003]/data[at0001]/items[at0005]/value/defining_code/code_string = 'final'
```

**Composition AQL** (targets the composition level):
```
SELECT c FROM EHR e CONTAINS COMPOSITION c
WHERE e/ehr_id/value='{{ehrid}}'
AND c/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/.../value/magnitude = 80
AND c/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/.../value/defining_code/code_string = 'final'
```

If `narrowToTemplate=true` (set when either `template` or `_profile` was provided in the request), the composition archetype is added to the composition AQL:
```
SELECT c FROM EHR e CONTAINS COMPOSITION c [openEHR-EHR-COMPOSITION.growth_chart.v0]
CONTAINS OBSERVATION [openEHR-EHR-OBSERVATION.body_weight.v2]
WHERE e/ehr_id/value='{{ehrid}}'
```

### What is compared

Each condition compares one attribute of a data value, on its strict AQL path (`RmAttributePins`):
- a mapping whose openEHR path ends in an attribute (`…/value/magnitude`, `…/defining_code/code_string`, `…/|code`)
  compares that attribute
- a mapping on a whole data value compares the attribute its type is compared by: `magnitude` of a `DV_QUANTITY` or
  `DV_COUNT`, `defining_code/code_string` of a `DV_CODED_TEXT` (a token search names a code; a manual mapping's
  fixed value is compared to the text, `value`), `numerator` of a `DV_PROPORTION`, `id` of a `DV_IDENTIFIER`, `value`
  of a text, date, time, boolean or ordinal. Of an element allowing several types, the type the mapping's
  `with.type` picks, otherwise the first the template lists that has one
- the attribute sits below the element's `value` for an ELEMENT (`items[at0004]/value/magnitude`), directly on a
  node that is a data value itself (`context/start_time/value`, `ism_transition/current_state/defining_code/code_string`)

Text is a string literal (`= '29463-7'`) in single quotes, in which a backslash, a single quote and a double quote
get a backslash before them (`= 'O\'Brien'`), so a search value can't leave it (`AqlLiterals`). Codes, identifiers,
dates and times are compared as text: escaped, not validated. A number or boolean is written unquoted (`= 80`,
`= true`) and has to be one: a number is what the AQL parser itself reads as one (`500`, `-1.5`, `.5`, `1e3`,
`1e-3`; not `1e+3`, an integer beyond a long or `1e309`), a boolean is `true` or `false` in any case. A value that is
not one is handled per template:

- the mappings of a template that compare the value to a number or boolean it is not can match nothing: that
  template's AQLs are left out, with a `WARNING` for the param naming the template;
- if no mapping the param matches can take the value, the request is rejected with `400`, naming the parameter but
  not the value;
- a mapping's own fixed (`manual`) value that its number or boolean attribute can't take is the mapping's fault:
  its template's AQLs are left out, with an `ERROR` for the param naming the template and mapping.

A param whose mappings all address a node
holding no data value (an EVENT, a CLUSTER, a participation …) is left out of the AQL and reported in
`unhandledParams` with type `WARNING`.

### AQL validation

Each generated AQL is parsed by the EHRbase AQL parser before being included in the response. If one of a template's
two AQLs (ENTRY, COMPOSITION) doesn't parse, it is left out and logged; the other one carries the same conditions. If
neither parses, the params compared in them get an `ERROR` in `unhandledParams`. The archetype-only AQL without
conditions then stands in only when no condition was left out like that, so a param never counts as handled while
its condition is missing.

---

## Response

`ToAqlResponse` contains:
- `aqls` — list of `AqlResponse(aql, type)` where type is `ENTRY` or `COMPOSITION`
- `unhandledParams` — list of `UnhandledParam(paramName, type, message)` for params that could not be mapped: `ERROR`
  when no mapping matches the param, `WARNING` when its mappings address nothing an AQL can compare, and `ERROR` for
  `_patientScope` when AQLs were left out because they could not be scoped by subject (see below; `_patientScope` is
  no FHIR search parameter, so it can't be confused with an unmapped `subject` parameter)

---

## Patient scope

Every generated AQL scopes the patient with the WHERE condition `e/ehr_id/value='{{ehrid}}'`, the first conjunct of
its WHERE. `AqlPatientScope` then:

- **with `ehrId`** fills the placeholder with it, escaped like a search value;
- **with `subject`** replaces the condition with the subject of the EHR:
  ```
  SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2]
  WHERE e/ehr_status/subject/external_ref/id/value = '194303193454'
  AND e/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1'
  ```
  The namespace condition is there only when a namespace is given. This is the form a federation gateway resolves
  the patient from, where one patient has a different `ehr_id` in each repository (Unio's
  `SubjectPredicateExtractor`). A quote or backslash in the id or the namespace is escaped with a backslash, so it
  stays inside the string literal.

  In a generated AQL the subject conditions are conjuncts at the top of the WHERE, as the `e/ehr_id/value` condition
  is: the conditions after them are ANDed, and the search values in them are escaped literals or validated numbers
  and booleans, so they can't add an OR. As a guard, and for `_query` AQLs, the result is parsed, and kept only if
  the subject conditions (on the EHR of the query, with the values given) are
  among the conditions reached from the root of its WHERE through AND alone, not below an OR or a NOT. The
  `{{ehrid}}` placeholder must occur exactly once, in that one WHERE condition. Every AQL for which this can't be
  shown is **left out** of `aqls` and counted in one `ERROR` for `_patientScope` in `unhandledParams`, so a caller
  that runs every AQL it gets never runs one that is not scoped to the patient;
- **with neither** leaves the placeholder for the caller to replace at query time.

`ehrId` and `subject` together, or a `subject` with a blank `id`, are rejected with `400`. A blank `namespace` counts
as none.

The `ehrId` filled into `{{ehrid}}` is escaped like a search value, so it stays in its literal whether a `_query`
writes the placeholder in single or double quotes.

A context's `_query` AQL is scoped the same way, as long as it scopes the patient with one WHERE condition
`<alias>/ehr_id/value='{{ehrid}}'` (or `"{{ehrid}}"`) ANDed at the top of its WHERE. One that scopes it in the FROM,
`EHR e[ehr_id/value='{{ehrid}}']`, or not at all, cannot be scoped by subject and is left out as above.
