---
name: ocdd-spec
description: "Write and revise project specifications for OCDD adopters. Use for adoption declarations, language-scoped paths, global constraints, normative sources, API/type contracts, examples, evidence requirements, acceptance, and conformance validation; exclude OCDD-standard and implementation changes."
---

# OCDD Project Specifications

Keep each obligation in one normative source and trace validation to it.

## Normative Sources

- Keep OCDD method, document path and format, and lifecycle rules in the adopted OCDD standard.
- Treat product requirements, issues, decision records, and other upstream materials as optional non-normative external references. Define any obligation they motivate in the owning project-wide or narrow contract, and keep their form, location, lifecycle, review, and acceptance outside OCDD project specifications.
- Put cross-unit constraints in the project-wide contract. Apply them to every in-scope contract without copies or inheritance-only backlinks.
- Put API, type, capability, error, and observable behavior in the narrow contract.
- Keep acceptance decisions and actual evidence outside normative sources while preserving the traceability required by the adopted OCDD version.
- Keep unobservable details in the implementation unless explicitly contractual.
- Use indexes to define contract-set membership and support discovery; do not let them restate or redefine contract obligations. Specialize a global obligation only when its normative source permits variation.

## Workflow

1. Resolve the project root and read applicable repository instructions.
2. Locate the project adoption declaration and read its adopted OCDD normative language version completely. Stop on an ambiguous version, language, scope, status, or normative source; honor status limits on conformance claims.
3. Inventory contract identifiers, normative sources, indexes, accepted revisions, evidence requirements, external inclusions, and links. Treat the entry as the project-wide contract as well as the contract index.
4. Classify the edit as draft, editorial, or accepted normative change. Keep accepted snapshots immutable and create the required draft version before changing their meaning.
5. Derive content, path, and format requirements from the adopted OCDD version. Edit the narrowest normative source, move instead of copy, keep indexes from redefining contracts, and do not invent project policy.
6. From the project root, run project-owned checks and verify changed links, identifiers, versions, normative sources, lifecycle transitions, and the final diff.

## Completion

Finish only when every changed obligation traces to one stable contract identifier, version, and normative source; inheritance is not duplicated; required evidence traces to that version and revision; paths and checks pass; and the diff contains only authorized specification work.
