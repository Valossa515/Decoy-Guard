# Contributing

Decoy Guard follows [SDD](https://github.com/valossa515/sdd). Read `CLAUDE.md` and `.agent/SKILLS.md` first. This file covers branches, commits and pull requests.

## Branches

`development` is the default branch. All new code starts from it and goes back to it through a pull request. `main` holds the stable version and only receives release PRs from `development` and hotfixes.

| Branch | Starts from | Merges into | How |
|--------|-------------|-------------|-----|
| `feature/ft-NNN-slug` | `development` | `development` | PR, squash |
| `fix/slug`, `refactor/slug`, `test/slug`, `docs/slug`, `chore/slug`, `ci/slug` | `development` | `development` | PR, squash |
| `development` | | `main` | Release PR, merge commit |
| `hotfix/slug` | `main` | `main`, then `development` | PR, squash, then a PR from `main` to `development` |

Names are lowercase kebab-case without accents, for example `feature/ft-004-pix-em-processamento` or `fix/login-error-message`. A feature branch uses the id of its spec. Branches opened by Claude Code sessions are named `claude/...` and are accepted too.

Nobody pushes to `main` or `development` directly, the repository rules block it.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/), in English, imperative mood:

```
<type>(<scope>): <summary in lowercase, no final period, up to 72 characters>

<optional body: why>

Refs: FT-NNN
```

| Type | When |
|------|------|
| `feat` | New behavior |
| `fix` | Bug fix |
| `test` | Tests only |
| `refactor` | Code change without behavior change |
| `docs` | README, `docs/`, `.agent/` (specs and plans included) |
| `ci` | `.github/` workflows and scripts |
| `build` | `pom.xml`, dependencies |
| `chore` | Anything else |

Scopes: `core`, `starter`, `demo`, `spec`, `ci`, `docs`, `deps`, `release`. The scope is optional. A change that breaks the public API adds `!` (`feat(core)!: ...`) and a `BREAKING CHANGE:` footer.

Examples: `feat(starter): shape decoy latency from learned real timings`, `test(demo): add parity test for pix latency`, `docs(spec): add FT-004 acceptance criteria`.

Pull requests into `development` are squashed, so the PR title becomes the commit. CI checks the title. Commits inside the branch should follow the convention but are not checked.

## Pull requests

1. Open the PR against `development` and fill in the template.
2. CI must pass: `build` (`mvn -B verify`), `specs` (TOON spec validation), `pr-title` and `branch-policy`.
3. Resolve every review conversation. `CODEOWNERS` asks the maintainer for review on PRs opened by others, and the maintainer is the one who merges. PRs opened by Claude Code sessions carry the maintainer's own account, so GitHub requests no review there; the maintainer reviews them before merging.
4. Squash and merge. The head branch is deleted automatically.

## Releases and hotfixes

* Release PR: open a PR from `development` to `main`, titled `chore(release): <what ships>`, listing the PRs it includes. Merge it with a merge commit. This updates `main` but does not create a version.
* Version: see "Versions and tags" below.
* Hotfix: branch `hotfix/slug` from `main`, PR to `main` with squash, then open a PR from `main` to `development` so both stay in sync.

## Versions and tags

Versions follow [SemVer](https://semver.org/), `X.Y.Z`, staying on `0.x` while the project is a prototype. A version is a tag `vX.Y.Z` on `main` with a GitHub Release. A release PR alone does not create one.

To cut a version, after the release PR is merged:

1. Actions, Release, Run workflow. Choose the `main` branch and type the version without the `v`, for example `0.1.0`.
2. The workflow checks the branch, the format and that the tag is new, sets the version in the poms for the build only, runs `mvn verify`, then creates the tag and the GitHub Release. The notes list the commits since the previous tag grouped by type, and the core and starter jars are attached.
3. Open a PR to `development` that raises the pom version to the next `-SNAPSHOT`, titled `chore(release): start X.Y.Z-SNAPSHOT`.

Released tags are never moved or deleted. A wrong version is fixed by releasing a new one.

## Repository settings

The rules for `main`, `development` and the `v*` tags live in `.github/rulesets/` as JSON and are imported in Settings, Rules, Rulesets, Import a ruleset. See `.agent/runbook.md` for the full list of settings.
