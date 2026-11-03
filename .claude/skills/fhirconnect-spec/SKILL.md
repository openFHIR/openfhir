---
name: fhirconnect-spec
description: >-
  Ground a FHIRConnect question in the specification and the mapping library before deciding it, and
  draft an issue when the spec is unclear. Use it whenever a change touches FHIRConnect semantics: a
  mapping key or type, a condition, a path operator, the preprocessor, slots, references, or what the
  engine does with a mapping. Use it too when writing or reviewing a mapping, and whenever the spec
  looks silent, ambiguous, self-contradictory or contradicted by the engine or the library.
---

# FHIRConnect spec and mapping library

openFHIR implements FHIRConnect. The engine is one reading of the spec, so a question about what a
mapping *means* is answered by the spec first and by the engine second. Where the spec does not
answer, that gap belongs to the spec's maintainers. Don't settle it quietly in engine code.

## The sources, and what each one is authority for

| Source | Where | Authority for |
|---|---|---|
| **The specification** | [SevKohler/FHIRconnect-spec](https://github.com/SevKohler/FHIRconnect-spec), branch `main`. Antora/AsciiDoc pages under `modules/ROOT/pages/`: `schema/`, `basics/` (conditions, path operators, preprocessor, variables, followedBy), `types-of-mappings/`, `types-of-mapping-files/` (context, model, extension mappings), `engine/` (bundles, references, ids, defaults, failing), `recurrence/` | What a mapping means. Normative. |
| **Spec issues and PRs**, open and closed | `gh issue list -R SevKohler/FHIRconnect-spec --state all --search "<term>"`, the same with `gh pr list --limit 200` | Decisions not yet written into the pages. Issues often end with a "Decision for now". Cite these the way you cite pages, and say they are not yet in the text. |
| **The mapping library** | [SevKohler/FHIRconnect-mapping-lib](https://github.com/SevKohler/FHIRconnect-mapping-lib), branch `main`. Model mappers under `model/<rm type>/<namespace>/`, project contexts under `projects/<namespace>/<project>/` | How mappings are written in practice, and which of them a semantic change would break. Not normative: a library mapping can be wrong. |
| **This engine** | The grammar classes in the `fhirconnect` module (what the engine accepts), the mapping engines in `core`, and the mappings and tests under `core/src/test/resources/` | What the engine does today. It is never evidence of what the spec says. |

## Getting the sources

- **Use a local checkout if there is one**, but read `main`, not whatever branch happens to be
  checked out:
  - `git -C <checkout> fetch -q origin`, then `git -C <checkout> show origin/main:<path>`
  - or `git -C <checkout> grep <term> origin/main -- <dir>`
- **Otherwise clone shallow into a scratch directory outside this repository**, with
  `git -C <scratch>/<name> fetch --depth 1 origin pull/<n>/head:pr-<n>` or `gh pr diff <n> -R SevKohler/FHIRconnect-spec.`. Never add the spec or the library to this repository.
- **When a feature follows an unmerged spec PR**, read that PR's branch as well, and say that the text
  is not on `main` yet.

## The scan, before deciding anything

Do it for every concept the change touches (a key, a mapping type, an operator, a behaviour):

1. **Spec text.** Grep the pages for the term and its synonyms. Quote what they say, with the file
   and section heading. If they say nothing, record that, because "silent" is a finding.
2. **Spec issues and PRs.** Search open and closed. An existing decision or open question changes
   what you do next.
3. **Mapping library.** Grep `model/` and `projects/` for the key or construct. Record:
   - how many mappings use it, with a few representative paths;
   - which variants of it they use;
   - which of them would change behaviour under the change you are considering.
4. **Engine.** Find the grammar class, the code path and the tests that cover the concept here.

Put the result in the report as one table per concept:

| Spec says | Issues / PRs | Mapping library | Engine does |
|---|---|---|---|

Then classify:

| Finding | What to do |
|---|---|
| Spec, library and engine agree | Proceed. Cite the spec section in the report. |
| The engine contradicts the spec | An engine bug. Fix it here (test-first). Name the library mappings whose output changes. |
| The spec is silent, ambiguous or contradicts itself | A **spec issue**. Draft it (below). |
| A library mapping contradicts the spec | A **mapping-library issue**. Draft it. Don't bend the engine to fit the mapping. |
| The engine follows an open spec issue or PR | Cite it in the code comment, the CHANGELOG entry and the report. |

## Drafting an issue

**Never file, comment or open a PR in either repository without the user's explicit go-ahead.**
They are public repositories that belong to someone else. Draft, show the draft, and wait.

1. **Search for duplicates first.** If an issue already covers it, draft a comment on that issue
   instead of a new one.
2. **Write for the spec's maintainers, who know FHIRConnect and nothing about this codebase.**
   - Keep the issue about the spec, not about openFHIR's internals.
   - Use a minimal mapping snippet and synthetic data. Never use real patient data, customer names
     or anything from a private project.
3. **Spec issues** follow the shape the spec repo already uses:

   ```markdown
   ## Current state
   What the spec says today, quoted with page and section; or that it says nothing.

   ## Question
   The one thing that needs deciding, with a minimal mapping example showing why it matters.

   ## Options
   1. **<option>**: what it means for mapping authors and engines; which library mappings it affects.
   2. ...

   ## Proposal
   The option you would pick and why. Leave this out if you have no basis to pick.
   ```

4. **Mapping-library issues** follow the existing bug reports:
   - **title:** the mapping and the symptom, e.g. "`<mapping>`: `<field>` dropped when `<condition>`";
   - **body:**
     - the mapping file and the lines;
     - a minimal input;
     - the expected and the actual output;
     - the spec section that says which is right.
5. **Hand the draft to the user**, with the repository it would go to and any duplicates you found.
   Once it is filed, put its number wherever the engine took a reading: the code comment, the
   CHANGELOG entry and the report.
