# Plan: Mobile-style front end (FT-002)

| Field | Value |
|---|---|
| Feature id | FT-002 (requested as DG-002) |
| Status | Approved 2026-11-09, implemented |
| Owner | Felipe Martins |
| Modules | `decoy-guard-demo` only |
| Feature spec | [mobile-front-end.spec.toon](../specs/features/mobile-front-end.spec.toon) |
| Acceptance | [mobile-front-end.acceptance.toon](../specs/acceptance/mobile-front-end.acceptance.toon) |

The SDD validator only accepts ids in the `FT-NNN` format, so the feature id is FT-002 and the requested id DG-002 is kept as `legacy-id` in the spec, the same way DG-001 became FT-001.

## Summary

The README promises a demo "showing both sessions side by side", but today the demo is only reachable with curl. This feature adds a static web page, served by the demo itself, with two phone mockups running the same bank app flow (PIN login, home with balance and statement, Pix, receipt). One phone is logged in with the real PIN and the other with the duress PIN. A third panel, the fraud desk, shows the silent alert and the held transactions, refreshing on its own. The page is the visual proof of the guiding rule: nothing inside the phone tells the two sessions apart.

## Goals

* Make the duress flow understandable in a few seconds, without curl, for the README and for live demos.
* Prove indistinguishability visually: the two phones run identical code against the existing API.
* Show the bank side (alert and held Pix) next to what the attacker sees.

## Non-goals

* A real mobile app, a SPA framework, a build step or npm dependencies.
* Changes to the library modules (`decoy-guard-core`, starter).
* Response time shaping (separate roadmap item).
* Authentication for the fraud desk (it stays a back office stand-in, as today).

## Functional requirements

| Id | Rule | Priority | Requirement |
|---|---|---|---|
| FR-01 | BR-001 | Must | The demo serves a static page at `/` (`src/main/resources/static/index.html`) with plain HTML, CSS and JS, no build step and no external CDN. |
| FR-02 | BR-002 | Must | The page shows two phone mockups side by side, each with the flow: PIN login, home (balance and statement), Pix form (key and amount), receipt, and a way back to home and to log out. |
| FR-03 | BR-003 | Must | Both phones are mounted from one `<template>` and one JS module. No per-phone branch, flag, class or text exists inside the phone. |
| FR-04 | BR-004 | Must | Phones consume only `/login`, `/account/balance`, `/account/statement` and `/account/pix`. Nothing rendered inside the phone reveals the session mode (CLAUDE.md rule 1), including errors such as "Saldo insuficiente" and a wrong PIN. |
| FR-05 | BR-005 | Must | The captions "Sessão normal (PIN 1234)" and "Sessão sob coação (PIN 4321)" are outside the phone frame, as legends of the demonstration. |
| FR-06 | BR-006 | Must | A third panel, "Mesa de fraude", lists the held transactions from `/fraud-desk/held` and refreshes on its own (polling every 2 seconds). |
| FR-07 | BR-007 | Must | The fraud desk panel shows the silent alert for the duress session (trigger and time), read from `GET /fraud-desk/alerts?session=`. |
| FR-08 | BR-008 | Must | Money is shown as R$ with pt-BR formatting (`R$ 48.750,32`), dates as `dd/mm` or `dd/mm/aaaa HH:mm`, all with `Intl` in `pt-BR` and `America/Sao_Paulo`. |
| FR-09 | BR-009 | Should | Modern Brazilian bank app look (rounded cards, bottom-aligned primary actions, hide balance toggle, Pix as the main shortcut), with a fictitious brand. |
| FR-10 | BR-010 | Must | Responsive: on narrow screens (phone width) the three panels stack vertically with no horizontal scroll. |

## Non-functional requirements

| Id | Requirement |
|---|---|
| NFR-01 | No new Java runtime dependency and no Node or npm in the Maven build. |
| NFR-02 | The fraud desk does not learn which phone is in duress from the page: it polls with both session tokens and shows whatever the server returns (the real session simply has nothing held). |
| NFR-03 | Prose in docs and README: plain sentences, no em dashes. |

## Scope

* **Layers affected:** demo static resources, demo tests, docs. No library change.
* **New files:**
  * `decoy-guard-demo/src/main/resources/static/index.html` (layout, legends, phone `<template>`, fraud desk panel)
  * `decoy-guard-demo/src/main/resources/static/styles.css`
  * `decoy-guard-demo/src/main/resources/static/app.js` (one `mountPhone(container)` used twice, plus the fraud desk poller)
  * `decoy-guard-demo/src/test/java/.../demo/FrontEndTest.java` (MockMvc)
  * `docs/media/demo.gif` and the script used to record it (Playwright, run by hand, not part of `mvn verify`)
  * `.agent/plans/mobile-front-end-tests.md` (test plan)
* **Modified files:** `README.md` (GIF at the top, roadmap item checked, link to this plan), `.agent/project-reference.md`, `.agent/decisions.md` (ADR-009), `FraudDeskController` (new `/fraud-desk/alerts`).

## Approach

* Static resources under `src/main/resources/static` are served by Spring Boot's default resource handling, and `index.html` is the welcome page at `/`. `SessionInterceptor` only covers `/account/**`, so the page needs no auth change.
* `app.js` exposes one function that clones the template into a container and keeps its own state (token, current screen). The page calls it twice. The JS never receives the PIN or the mode as a parameter: the user (or the GIF script) types the PIN on the keypad.
* Each phone publishes its token to the page orchestrator after login only so the fraud desk can poll. The orchestrator does not know which token is which.
* The fraud desk polls `/fraud-desk/alerts?session=<token>` and `/fraud-desk/held?session=<token>` for every open token every 2 seconds and renders the union.
* Architecture contract: [mobile-front-end.contract.toon](../specs/contracts/mobile-front-end.contract.toon).
* Errors: the phone shows the `error` field of the JSON body (422) as returned, and a fixed "PIN incorreto" on 401. Same code path in both phones.

## Tasks

1. [x] Static page skeleton: layout with three panels, legends outside the frames, phone template.
2. [x] Phone flow in `app.js`: keypad login, home with balance and statement, Pix form, receipt, logout.
3. [x] Fraud desk panel with polling, plus `GET /fraud-desk/alerts`.
4. [x] Styles: bank look, pt-BR formatting, responsive stacking.
5. [x] `FrontEndTest` (MockMvc) covering the acceptance criteria below.
6. [x] Record the GIF with Playwright and put it at the top of the README; check the roadmap item.
7. [x] Review against the five rules, `mvn verify`, commit on `main`.

## Database changes

None.

## Risks and open questions

1. **Showing the alert (decided: option A, approved by Felipe on 2026-11-09).** No endpoint exposes the silent alert today. It only goes to the log through `LoggingSilentAlertPublisher`.
   * **Option A (recommended):** add `GET /fraud-desk/alerts?session=` to the existing back office controller, returning a list with zero or one `SilentAlert` built from `DuressSessionRegistry.resolve(session)` via `SilentAlert.of(...)`. No new storage, no change to core or starter, and it reflects exactly the alert raised at login. It lives next to `/fraud-desk/held`, which already reveals the mode by design.
   * **Option B:** no backend change. The panel shows an "alert" card only once a held transaction exists. Simpler, but the alert would appear late (after the first Pix instead of at login), which misrepresents how the product works.
2. **Real account state is shared.** `RealAccountService` is a singleton, so a Pix from the normal phone lowers the real balance for every later session until the demo restarts. Acceptable for a demo; the GIF only sends Pix from the duress phone.
3. **The fraud desk endpoint is public in the demo.** Already true today. The page must never call it from inside a phone; only the separate panel does.
4. **Network traffic differs between phones** (the fraud desk polls both tokens, and only the duress one returns data). This is outside the threat model (network interception is out of scope) and the calls are made by the panel, not by the phone.

## Acceptance criteria

* [x] AC-01: `GET /index.html` returns 200 with HTML containing the two phone slots, the fraud desk panel and the phone template.
* [x] AC-02: `GET /` serves the page (welcome page forwards to `index.html`).
* [x] AC-03: `styles.css` and `app.js` are served with 200 and the right content type.
* [x] AC-04: The phone template contains no mode marker (no "coação", "duress", "decoy", "normal", "real", "1234" or "4321"), and the legends with the PINs are outside it.
* [x] AC-05: The page references only the existing endpoints from inside the phone code and contains no per-phone mode parameter.
* [x] AC-06: The fraud desk endpoint used by the page returns the held Pix of the duress session and nothing for the normal session and one alert with trigger `DURESS_CREDENTIAL`.
* [x] AC-07: The page uses no external host (no `http://` or `https://` in script or stylesheet sources).
* [x] AC-08: The README shows the GIF of the duress flow at the top (manual check during review).

## Test coverage

| Criteria | Test |
|---|---|
| AC-01 to AC-05, AC-07 | `FrontEndTest` (demo, MockMvc) |
| AC-06 | `FrontEndTest` (demo, MockMvc through the API the page calls) |
| AC-08 | Review checklist ([test plan](mobile-front-end-tests.md)) |
