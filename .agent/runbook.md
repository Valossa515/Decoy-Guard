# Runbook

## Prerequisites
- Java 21 (CI uses Temurin 21)
- Maven 3.9+
- No database or external service

## How to Run
```bash
mvn -pl decoy-guard-demo spring-boot:run
# Demo on http://localhost:8080. PINs: 1234 (real), 4321 (duress).
```

Walkthrough:
```bash
TOKEN=$(curl -s localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"pin":"4321"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s localhost:8080/account/balance -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/account/pix -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"key":"golpista@exemplo.com","amount":200}'
curl -s "localhost:8080/fraud-desk/held?session=$TOKEN"
```

## How to Test
```bash
mvn verify                          # all modules, required before declaring any change done
mvn -pl decoy-guard-core test       # one module
bash .agent/scripts/validate-spec.sh .agent/specs   # validate TOON specs
```

## Build and Release
CI (`.github/workflows/ci.yml`) runs on push to `main` and `development` and on PRs into either. Jobs, which are also the required checks:

| Job | What it runs | When |
|-----|--------------|------|
| `build` | `mvn -B verify` on Temurin 21, uploads Surefire reports on failure | Always |
| `specs` | `bash .agent/scripts/validate-spec.sh .agent/specs` | Always |
| `pr-title` | `.github/scripts/check-pr-title.sh` (Conventional Commits) | PRs |
| `branch-policy` | `.github/scripts/check-branch-policy.sh` (branch name, only `development` or `hotfix/*` into `main`) | PRs |

Release: PR from `development` to `main`, titled `chore(release): ...`, merged with a merge commit.
Hotfix: `hotfix/slug` from `main`, PR to `main` (squash), then a PR from `main` to `development`.
Dependabot (`.github/dependabot.yml`) opens weekly PRs for Maven and GitHub Actions against `development`.

Repository settings, done by hand by the owner:
- Default branch: `development`.
- Pull requests: allow merge commits and squash merging, no rebase merging, always suggest updating branches, delete head branches automatically.
- Rulesets: import `.github/rulesets/main.json` and `.github/rulesets/development.json` (Settings, Rules, Rulesets, Import a ruleset). Both require a PR, the CI checks above and resolved conversations, and block force pushes and deletion. `development` also requires the branch to be up to date with it. `main` does not, because every release adds a merge commit to `main` that `development` never receives (it only takes squashed PRs), so a release PR could never be up to date. No bypass. Zero required approvals, because PRs opened by Claude Code are authored by the owner's account and GitHub does not let an author approve their own PR. `.github/CODEOWNERS` requests the owner's review on PRs opened by others (Dependabot, contributors). On PRs opened by Claude Code the owner is the author, so the review is the owner reading the PR before merging.

- No migrations.
- Not published yet. Maven Central under `io.github.valossa515` is on the roadmap.

## Configuration
| Property | Default | Effect |
|----------|---------|--------|
| `decoy-guard.enabled` | `true` | `false` disables routing, the filter and the default SPI beans. Decoys stay hidden from injection. |
| `decoy-guard.latency-shaping.enabled` | `true` | `false` stops observing real calls and holding decoy calls. |
| `decoy-guard.latency-shaping.window-size` | `500` | Real durations kept per routed method and outcome. |
| `decoy-guard.latency-shaping.min-observations` | `20` | Observations needed before they replace the fallback range. |
| `decoy-guard.latency-shaping.fallback-min` / `fallback-max` | `150ms` / `400ms` | Decoy duration range while there are too few observations. |
| `demo.core-banking-latency.min` / `max` | `120ms` / `350ms` | Demo only. Simulated hop to core banking in `RealAccountService`. Tests set it to zero, except `ResponseTimeParityTest`. |
| `demo.real-pin` | `1234` | Demo only |
| `demo.duress-pin` | `4321` | Demo only |

## Troubleshooting
- **Context fails with "Two @Decoy beans stand in for ..."** -> only one `@Decoy` per interface is allowed.
- **`@Decoy on X lists Y, which it does not implement as an interface`** -> `@Decoy(value)` may only list interfaces the bean implements.
- **`NoSuchBeanDefinitionException` or a class cast when injecting a real service** -> inject the interface, not the concrete class. Routed beans are JDK interface proxies.
- **Decoy never used in `@Async` or reactive code** -> `DuressContext` is thread-bound. Propagate it explicitly.
