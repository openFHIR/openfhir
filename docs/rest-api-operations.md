# FHIRconnect REST API operations: `$tofhir` and `$toopenehr`

openFHIR implements the REST API surface defined by the FHIRconnect specification
(`engine/rest-api.adoc` + the FHIR IG under `rest/` in the spec repository): two FHIR
Operations-framework endpoints mounted at the server root, alongside the existing direct-payload
endpoints under `/openfhir/*`.

## `POST [base]/$tofhir`

Maps an openEHR Composition to FHIR.

**Request** — a FHIR (R4) `Parameters` resource (`application/fhir+json`; plain
`application/json` is also accepted):

| parameter | cardinality | type | notes |
|---|---|---|---|
| `composition` | 1..1 | valueString | stringified openEHR Composition, flat or canonical |
| `templateId` | 0..1 | valueString | **required** when `composition` is flat (a flat payload cannot carry its template id inline) |
| `context` | 0..1 | (nested parts) | `ehr_id` (valueString), `patient` (valueReference), `who` (valueReference), `onBehalfOf` (valueReference) |

Example:

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "composition", "valueString": "{ \"growth_chart/...\": \"...\" }" },
    { "name": "templateId", "valueString": "Growth chart" },
    { "name": "context", "part": [
      { "name": "ehr_id", "valueString": "7d44b88c-4199-4bad-97dc-d78268e01398" },
      { "name": "patient", "valueReference": { "reference": "Patient/123" } },
      { "name": "who", "valueReference": { "reference": "Practitioner/456" } },
      { "name": "onBehalfOf", "valueReference": { "reference": "Organization/789" } }
    ]}
  ]
}
```

**Response** — a FHIR `Bundle` (`application/fhir+json`) containing the mapped resources plus:

- a **`Provenance`** entry (always, appended last): `target` references every mapped entry,
  `recorded` is the transformation time, `agent.who` is `context.who` when supplied, otherwise the
  configured engine device (`openfhir.provenance.device-reference` / `device-display`),
  `agent.onBehalfOf` mirrors `context.onBehalfOf`, and `entity` (role `source`) carries the
  `templateId` and `ehr_id` used, when known.
- an **`OperationOutcome`** entry (only when the mapping reported gaps): severity `warning`,
  code `incomplete`, one issue per skipped/unmappable element. A partial result coexists with the
  issues; gaps are reported instead of silently dropped.

**Context semantics** — empty (and only empty) top-level `subject`/`patient` Reference children of
the mapped resources are filled with a patient reference resolved in this order: caller-supplied
`context.patient` first, then engine-side resolution of `context.ehr_id` via the pluggable
`PatientResolverInterface` bean (an MPI/demographics hook; the default `NoOpPatientResolver`
resolves nothing). Neither is ever required.

## `POST [base]/$toopenehr`

Maps a FHIR Bundle (or a single resource — accepted liberally) to an openEHR Composition.

**Request** — the FHIR Bundle itself as the body (no `Parameters` wrapper), with query parameters:

- `templateId` (optional): forces a specific context mapper.
- `format`: `canonical` (default) or `flat`.

**Response** — a FHIR (R4) `Parameters` resource:

- `composition` (valueString): the mapped Composition in the requested format;
- `outcome` (resource, optional): an `OperationOutcome` with reported gaps — again, a partial
  result may coexist with issues.

## Query parameters and precedence

`templateId` (both operations), `format` (`$toopenehr`) and the short context fields of `$tofhir`
(`ehr_id`, `patient`, `who`, `onBehalfOf`) may be sent as query parameters. When a value appears in
both the body and the query string, **the body takes precedence**; query parameters only fill
fields missing from the body and are never required.

## Errors

Errors on the operations endpoints are returned as `OperationOutcome` (`application/fhir+json`) —
e.g. a flat composition without `templateId` yields `400` with issue code `required`, an
unparseable body `400` with `structure`. The legacy `/openfhir/*` endpoints keep their plain-text
error bodies.

## Media types

- `application/fhir+json` — the FHIR envelope on the operations endpoints (plain
  `application/json` is accepted as well).
- `application/openehr+json` — labels an unwrapped openEHR JSON payload on the direct forms.

## Direct payload invocation (convenience form)

The pre-existing endpoints are the spec's "direct" form and stay fully backward compatible:

- `POST /openfhir/tofhir?templateId=` — body is the openEHR Composition itself
  (`application/openehr+json`); response is the mapped FHIR resource itself.
- `POST /openfhir/toopenehr?templateId=&format=flat|canonical` — body is the FHIR resource itself
  (`application/fhir+json`); response is the Composition itself. The deprecated `flat=true|false`
  parameter is still accepted; `format` wins when both are set.

No Provenance/OperationOutcome handling is applied on the direct forms — their responses are
unchanged.

## Version note (R4-pinned envelope)

The operation envelope — `Parameters`, `Bundle` post-processing, `OperationOutcome`, `Provenance`
— is pinned to **FHIR R4**, matching the FHIRconnect IG. Context mappings targeting STU3/R4B/R5
still work (the mapped payload travels as-is), but the R4-only post-processing passes (Provenance
generation, subject population, the warnings entry) are skipped for non-R4 bundles.

## Deployment note (`$` in paths)

The operations are mounted at the server root (`/$tofhir`, `/$toopenehr`). Some proxies/gateways
percent-encode `$`; Spring matches the literal character, so make sure intermediaries pass it
through unchanged.
