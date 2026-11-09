---
name: bootstrap
description: Use this skill when the user asks for @bootstrap, project initialization, or wants the agent to map project context before coding. It creates and maintains project context docs and links them in .agent/SKILLS.md.
stack: shared
versions: "SDD 1.x"
---

# Bootstrap — Project Context Initialization

> ## 🚨 MANDATORY EXECUTION CONTRACT — READ BEFORE ANYTHING ELSE
>
> **This skill produces FILES ON DISK, not chat messages.**
>
> You MUST use a **file-creation tool** (Write, write_to_file, create_file, or equivalent) to write each file.
> Showing content in a chat code block is NOT writing a file. You must call the tool that persists the file to the filesystem.
>
> When you receive `@bootstrap`, your ONLY job is:
> 1. Scan the codebase **silently** — produce ZERO chat output during scanning
> 2. Create **9 files** inside the `.agent/` directory using file-writing tools
> 3. After ALL 9 files exist on disk, report the checklist
>
> **You are DONE only when all 9 files exist on disk.**
>
> ### SELF-CHECK before every step:
> - Am I about to write content **in a chat message**? → STOP → use the file-writing tool instead.
> - Am I about to show a code block with file contents? → STOP → use the file-writing tool instead.
> - Am I about to present a summary/overview in chat? → STOP → that content belongs in a `.agent/` file.

## Trigger phrases

- `@bootstrap`
- `@bootstrap` followed by a description of what the project does
- "bootstrap do projeto"
- "initialize project context"
- "prepare .agent docs"

---

## Two sources: the code and the project description

Bootstrap writes the context docs from two sources, and either one can be missing:

| Source | What it tells you | Where it goes |
|--------|-------------------|---------------|
| **The codebase** (Step 1 scan) | What the system does *today*: modules, endpoints, schema, conventions, build | All files |
| **The project description** — anything the user writes with `@bootstrap`: purpose, problem, users, main flows, business rules, constraints, domain terms, decisions already made | *Why* the system exists and what it must do — things code rarely shows | `context.md` (Problem, Objectives, Personas, Main Flows, Constraints), `glossary.md` (business terms), `backlog_rules.md` (business rules), `decisions.md` (decisions the team already made) |

Rules:

- **The description is a primary source, not a guess.** Facts the user states are explicit requirements (see `anti-invention`): write them down as given. Do not embellish them or fill gaps around them.
- **The code wins for what exists; the description wins for intent.** When they disagree (e.g. the description says "multi-tenant" but nothing in the code filters by tenant), write both and mark the line `⚠️ Divergence: description says X, code shows Y` in `context.md`. Never silently pick one.
- **New project, little or no code:** write all 9 files from the description. Sections that only code can fill (class map, endpoints, schema, build commands) get `TODO: no code yet`. Anything the description plans but the code does not have yet is labelled **(planned)**, never presented as implemented.
- **No description given:** business sections the code cannot reveal (Problem, Objectives, Personas, Constraints) get `TODO: describe in @bootstrap`. List them in the Step 10 report and suggest re-running `@bootstrap` with a description.
- **Re-running with only a description** (to add or correct what the project does): update the business sections of the existing files and keep everything else as it is.

---

## ❌ FORBIDDEN — Read this BEFORE executing

These are the most common failure modes. If you catch yourself doing any of these, STOP and correct immediately:

1. **Presenting a project summary in chat without creating files.** If you scanned the project and wrote an overview in the chat but created zero files → you FAILED. Go execute Steps 1–9 using file-writing tools.
2. **Stopping after scanning the project.** Scanning is the first half of Step 1. Writing the file is the second half. You are not done until Step 10.
3. **Asking "should I create the files?"** — YES. Always. That is the entire point of `@bootstrap`. Just do it.
4. **Showing file content in a chat code block instead of writing the file.** If you are about to type ` ```markdown ` with the contents of `project-reference.md` in a chat message → STOP → use a **file-creation tool** instead.
5. **Leaving placeholder text** like `[project name]` or `[describe...]` when the data is in the codebase.
6. **Writing only one or two files and then stopping.** All 9 files are required. Check the list in Step 10 and ensure every single one exists.

---

## Execution steps

Follow these steps **in order**. Each step MUST end with a **file being written to disk using a file-writing tool**. Do NOT proceed to the next step until the current file has been written.

---

### Step 1 — Scan & write `.agent/project-reference.md`

**Scan:** Read `README`, build file (`pom.xml`, `build.gradle`, `*.csproj`, `package.json`), CI config, and the full source tree. Map every package/namespace, class, controller, service, repository, entity, and config. Identify architecture style, conventions, integrations, and domain vocabulary.

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/project-reference.md` with:
- Follow template structure from `templates/common/project-reference.md`
- Project overview (name, type, stack, build tool, runtime version)
- Architecture style and layer map
- **Complete class/module map** — every class found, with path and responsibility
- Available endpoints (from controllers/routes)
- Database schema (from entities/migrations)
- Conventions summary (naming, structure, technical choices)
- Domain glossary (entities, enums, business terms)
- Dependencies table (from build file)
- How to build, run, and test

> This is the PRIMARY deliverable. It must be comprehensive and populated with real data.
> MUST NOT contain placeholder text like `[project name]` or `[describe...]` — use real values from the scan or `TODO: <reason>` for genuinely unknowable info.

**✅ Checkpoint:** The file `.agent/project-reference.md` now exists on disk. If it does not, go back and write it using a file-writing tool before continuing.

---

### Step 2 — Write `.agent/context.md`

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/context.md` with:
- Follow template from `templates/common/context.md`
- Project overview, problem statement, objectives, personas, main flows, endpoints, constraints, dependencies
- Populate from the data already collected in Step 1 **and from the project description, if one was given** (see "Two sources" above): problem, objectives, personas and constraints come mostly from the description

**✅ Checkpoint:** The file `.agent/context.md` now exists on disk.

---

### Step 3 — Write `.agent/architecture.md`

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/architecture.md` with:
- Follow template from `templates/common/architecture.md`
- Architecture style, layer responsibilities, package structure, integration points
- Populate from the data already collected in Step 1

**✅ Checkpoint:** The file `.agent/architecture.md` now exists on disk.

---

### Step 4 — Write `.agent/conventions.md`

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/conventions.md` with:
- Follow template from `templates/common/conventions.md`
- Naming patterns, code structure, DI style, error handling, test conventions
- Populate from the data already collected in Step 1

**✅ Checkpoint:** The file `.agent/conventions.md` now exists on disk.

---

### Step 5 — Write `.agent/runbook.md`

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/runbook.md` with:
- Follow template from `templates/common/runbook.md`
- Build, run, test, deploy, and migration commands
- Environment setup, required tools, common troubleshooting

**✅ Checkpoint:** The file `.agent/runbook.md` now exists on disk.

---

### Step 6 — Write `.agent/glossary.md`

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/glossary.md` with:
- Follow template from `templates/common/glossary.md`
- Domain terms, entity names, enum values, abbreviations from the codebase, plus the business terms the project description defines

**✅ Checkpoint:** The file `.agent/glossary.md` now exists on disk.

---

### Step 7 — Write `.agent/decisions.md`

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/decisions.md` with:
- Key architecture decisions found in the code (database choice, ORM, auth mechanism, etc.)
- Format as lightweight ADRs (context → decision → consequences)

**✅ Checkpoint:** The file `.agent/decisions.md` now exists on disk.

---

### Step 8 — Write `.agent/backlog_rules.md`

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/backlog_rules.md` with:
- Follow template from `templates/common/backlog_rules.md`
- Include the business rules stated in the project description

**✅ Checkpoint:** The file `.agent/backlog_rules.md` now exists on disk.

---

### Step 9 — Write `.agent/SKILLS.md`

**⚠️ ACTION REQUIRED:** Use your file-writing tool to **write the file** at `.agent/SKILLS.md` with:
- Active skills list
- Section **`## Project Overrides`** (keep this exact heading — installers, commands and docs refer to it)
- Links to `project-reference.md`, `context.md`, `architecture.md`, and `runbook.md`
- How the repository and API layers are tested (real database, in-memory, rendered queries, `WebApplicationFactory`/`MockMvc`, ...) — the DoD's integration-test item is checked against this, so record it whenever the project does not run real integration tests

**Reconcile the Active Skills tables with the real stack.** The installed template ships generic
`Scope` descriptions (e.g. Database → "Entities, DbContext"; Error handling → "Exception mapping,
RFC 7807"). For every row whose scope names a technology or format the scan did **not** find, rewrite
the scope to what the project actually uses (e.g. "Entities, MongoDB repositories, indexes";
"Exception mapping, `{ code, message, field }` — see Project Overrides"). Never leave a table row that
contradicts `## Project Overrides` — agents read the tables first. Keep the skill links unchanged.

**✅ Checkpoint:** The file `.agent/SKILLS.md` now exists on disk, and no Active Skills row names a technology or format that Project Overrides says the project does not use.

---

### Step 10 — Self-check & Report

**Before reporting, verify ALL 9 files were written to disk.** Count them:

1. `.agent/project-reference.md`
2. `.agent/context.md`
3. `.agent/architecture.md`
4. `.agent/conventions.md`
5. `.agent/runbook.md`
6. `.agent/glossary.md`
7. `.agent/decisions.md`
8. `.agent/backlog_rules.md`
9. `.agent/SKILLS.md`

**If ANY file is missing:** Go back to the corresponding step and write it NOW using a file-writing tool. Do NOT report until all 9 files exist.

**Only after all 9 files are confirmed written**, output this checklist:

```
✅ Bootstrap complete. Files created:
- .agent/project-reference.md
- .agent/context.md
- .agent/architecture.md
- .agent/conventions.md
- .agent/runbook.md
- .agent/glossary.md
- .agent/decisions.md
- .agent/backlog_rules.md
- .agent/SKILLS.md
```

Then list the sections left as `TODO: describe in @bootstrap` and any `⚠️ Divergence` lines, so the user knows what to describe or decide.

---

## Output quality rules

- Every section must contain **real project data** — from the codebase scan or the user's project description, never invented.
- Class/module descriptions must be specific (e.g., "Handles order creation and stock validation") not generic (e.g., "Service layer").
- Architecture docs must reflect the actual folder structure and class relationships.
- Use `TODO: <reason>` instead of inventing facts — but only for genuinely unknowable info.
- Keep docs concise, practical, and project-specific.

## Quick reference

```text
@bootstrap
```

```text
@bootstrap
This project is <what it does, for whom, main flows, business rules, constraints>.
```

```bash
make bootstrap STACK=spring-boot TARGET=$(pwd)
```
