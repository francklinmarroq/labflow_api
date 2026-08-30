# Constitution — LabFlow API

Non-negotiable, short, verifiable principles.

1. **Stack simplicity** — Spring Boot + PostgreSQL + JWT; no extra framework, library, or abstraction without verifiable justification.
2. **Spec first** — Every behavior change starts from a spec (OpenSpec); code implements the spec, never the reverse.
3. **Logic vs interface** — Controllers only orchestrate and return DTOs; business logic lives in services.
4. **Tests** — Every behavior change ships with tests that verify it; no tests, no merge.
5. **Persistence** — PostgreSQL with idempotent migrations in `schema.sql`; no Flyway/Liquibase or automatic DDL in prod.
6. **Language** — Code (variables, classes, methods) in English; comments, commits, and error messages in Spanish.
