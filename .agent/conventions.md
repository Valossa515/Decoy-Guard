# Project Conventions

## Naming
- **Classes:** PascalCase. Real and decoy pairs are `Real{Name}` and `Decoy{Name}` implementing `{Name}` (`RealAccountService`, `DecoyAccountService`, `AccountService`).
- **SPI defaults:** `InMemory{Spi}` in core, `Logging{Spi}` or `{Mechanism}{Spi}` in the starter (`LoggingSilentAlertPublisher`, `BearerTokenSessionIdExtractor`).
- **Methods:** camelCase, short verbs (`activate`, `resolve`, `release`, `hold`, `findBySession`, `pix`).
- **Endpoints:** lowercase, kebab-case, no version prefix (`/account/balance`, `/fraud-desk/held`).
- **Constants:** UPPER_SNAKE (`DECOY_OPENING_BALANCE`, `ENABLED_PROPERTY`).
- **Configuration properties:** prefix `decoy-guard.` (`decoy-guard.enabled`). Demo properties under `demo.`.
- **Tests:** `{Subject}Test`, method names are camelCase sentences (`duressSessionIsRoutedToDecoy`, `firstActivationWins`).

## Folder / Package Structure
```
decoy-guard-core/src/main/java/io/github/valossa515/decoyguard/core/
    flat package: model records, enums, SPIs, InMemory defaults, DuressActivator
decoy-guard-spring-boot-starter/src/main/java/io/github/valossa515/decoyguard/spring/
    flat package: @Decoy, post processor, filter, extractor, publisher, auto-configuration
decoy-guard-spring-boot-starter/src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
decoy-guard-demo/src/main/java/io/github/valossa515/decoyguard/demo/
    DemoApplication, ClockConfiguration
    account/   AccountService (+ DTO records), Real/DecoyAccountService, controller, exception, ids
    auth/      AuthController, DemoSessions, SessionInterceptor
    fraud/     FraudDeskController
decoy-guard-demo/src/main/resources/application.yml
<module>/src/test/java/... mirrors the main package
.agent/      SDD context, skills, agents, specs and plans
docs/        threat-model.md
```

## Code Style
- Model types are Java records with compact constructors that validate with `Objects.requireNonNull`.
- DTOs of a routed interface are nested records inside the interface, so both implementations share them.
- Classes that must not be extended or instantiated are `final` with a private constructor (`DuressContextHolder`, `EndToEndIds`).
- Configuration classes use `@Configuration(proxyBeanMethods = false)`.
- Time comes from an injected `java.time.Clock`, never `Instant.now()` in production code.
- Money is `BigDecimal`.
- Javadoc on every public type explains the duress-related intent.

## APIs
- **Versioning:** none.
- **Error format:** small JSON map (`{"error":"Saldo insuficiente"}`) from a controller `@ExceptionHandler`, or a bare status (401). No RFC 7807. The error must be identical in both modes.
- **Pagination:** none.
- **Auth:** demo only, opaque `Authorization: Bearer <token>`.

## Database
- No database. Storage is an SPI with in-memory defaults (`ConcurrentHashMap`, `CopyOnWriteArrayList`).
- New storage concerns become an SPI in core with an in-memory default (CLAUDE.md rule 5).

## Testing
- **Framework:** JUnit 5 with AssertJ (Hamcrest only for `jsonPath` matchers).
- **Mock library:** none. Tests use hand-written lambdas and small fakes.
- **Core:** plain unit tests, `Clock.fixed` for time.
- **Starter:** `ApplicationContextRunner` with nested test beans and the auto-configuration.
- **Demo:** `@SpringBootTest` + `@AutoConfigureMockMvc` end-to-end through HTTP, with parity checks between modes.
- **Integration tests with a real database:** not applicable, there is no database.
- **Coverage tool:** none configured.

## Dependency Injection
- **Style:** constructor injection only.
- **Routed services:** injected by interface, never by concrete class (the routing proxy is interface-based).
- **`@Decoy` beans:** never injected anywhere.

## Error Handling
- **Strategy:** exceptions thrown by services, mapped in the controller with `@ExceptionHandler`.
- **Custom exceptions:** `InsufficientFundsException` (demo), shared by real and decoy.
- **Alert failures:** caught and logged inside `DuressActivator`, never propagated.
- **Logging:** `System.Logger` in core (no dependency), commons-logging in the starter. Never log the mode in anything returned to the client. The default alert log contains trigger and time, not the session id.

## Git
- **Default branch:** `development`. `main` is the stable version and only receives release PRs and hotfixes.
- **Branch names:** `feature/ft-NNN-slug`, `fix/slug`, `refactor/slug`, `test/slug`, `docs/slug`, `chore/slug`, `ci/slug`, `hotfix/slug`. Lowercase kebab-case, no accents (`feature/ft-004-pix-em-processamento`).
- **Commits:** Conventional Commits in English, `<type>(<scope>): <summary>`, imperative, lowercase summary up to 72 characters, no final period.
  - Types: `feat`, `fix`, `test`, `refactor`, `docs` (includes `.agent/` specs and plans), `ci`, `build`, `chore`.
  - Scopes: `core`, `starter`, `demo`, `spec`, `ci`, `docs`, `deps`, `release`. Optional.
  - Breaking change: `!` after the scope and a `BREAKING CHANGE:` footer.
  - Examples: `feat(starter): shape decoy latency from learned real timings`, `docs(spec): add FT-004 acceptance criteria`.
- **Merging:** squash into `development` (the PR title becomes the commit), merge commit from `development` into `main`.
- Full rules in `CONTRIBUTING.md`.

## Docs
- Prose in docs and README: plain sentences, no em dashes.
- Update the roadmap checklist in `README.md` when an item ships.
