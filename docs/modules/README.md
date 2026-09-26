# Module design docs

There is one file per backend module (`<module>.md`), written when the module is first built and updated alongside it. Each doc covers:

- **Purpose:** what the module owns and what it deliberately does not own
- **Public API:** services, DTOs, and the events it publishes and consumes
- **Data:** its schema and tables, indexes, and retention
- **Endpoints:** the HTTP routes it serves
- **External ports:** interfaces to outside services, and their adapters
- **Open questions:** `TODO(product):` items

See [`../architecture.md`](../architecture.md) for the module map and allowed dependencies.
