<!-- PR title: <type>(<scope>): <summary>, see CONTRIBUTING.md. It becomes the squash commit. -->

## What

<!-- One sentence. For a release PR (development to main), list the PRs it includes. -->

## Why

<!-- The problem this solves. Feature id: FT-NNN, or "none" for chores. -->

## How

<!-- The approach in a few lines. -->

## SDD artifacts

<!-- Links, or "none" for a small fix that skips plan and architect. -->
- Plan:
- Feature spec and acceptance:
- Contract and architecture notes:

## Type of change

- [ ] feat
- [ ] fix
- [ ] refactor
- [ ] test
- [ ] docs
- [ ] ci, build or chore

## Pipeline

- [ ] Plan and specs validated with `validate-spec.sh`, or a small fix that skips them
- [ ] Tests reference the acceptance criteria ids
- [ ] Parity test in the demo for any change to a routed interface
- [ ] Review done with `.agent/agents/reviewer.md`, no open red items
- [ ] `mvn verify` passes locally

## Invariants (CLAUDE.md)

- [ ] The session mode is not observable by the client
- [ ] Real and decoy share DTOs, exceptions and id formats
- [ ] `@Decoy` beans are not injectable; routed services are injected by interface
- [ ] Alert failures never fail the request
- [ ] A new storage or delivery concern is an SPI in core with a default

## Docs

- [ ] README roadmap updated, if an item shipped
- [ ] `.agent/decisions.md` updated, if there is a new decision

## Breaking changes

<!-- "None." or what breaks and how to migrate. -->
