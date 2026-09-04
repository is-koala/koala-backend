# Domain Docs

Before exploring the repository, read:

- Root `CONTEXT.md`, when present.
- Root `CONTEXT-MAP.md`, when present.
- Relevant decisions under `docs/adr/`.

This repository uses the single-context layout:

```text
/
├── CONTEXT.md
├── docs/adr/
└── src/
```

Use the terminology defined in `CONTEXT.md`. If work contradicts an existing ADR, report the conflict explicitly instead of silently overriding the decision.

Missing domain files are not errors. Domain-modeling creates them lazily when terminology or architectural decisions are resolved.
