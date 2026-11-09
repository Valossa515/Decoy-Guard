# Test plan: Mobile-style front end (FT-002)

Acceptance criteria: [mobile-front-end.acceptance.toon](../specs/acceptance/mobile-front-end.acceptance.toon). Contract: [mobile-front-end.contract.toon](../specs/contracts/mobile-front-end.contract.toon).

## Strategy

There is no new routed interface method, so no new real or decoy parity test is needed. The existing `DuressFlowTest` keeps covering parity of the API the phones call. The new risk is the page itself: it must be served, must stay free of mode markers inside the phone, and must not grow a per-phone branch. These are checked with MockMvc against the served files. Browser behavior is checked by hand through the GIF recording script and a phone width screenshot, because `mvn verify` runs no browser.

## Automated (`FrontEndTest`, demo, `@SpringBootTest` + MockMvc)

| Test | Criteria |
|---|---|
| `pageIsServed` | AC-01 |
| `rootServesThePage` | AC-02 |
| `assetsAreServed` | AC-03 |
| `phoneTemplateHasNoModeMarker` | AC-04 (rule 1) |
| `phonesShareOneModuleAndUseOnlyTheAccountApi` | AC-05 |
| `fraudDeskShowsOnlyTheDuressSession` | AC-06 |
| `pageUsesNoExternalHost` | AC-07 |

## Manual

| Check | Criteria |
|---|---|
| `node docs/media/record-demo.mjs` on a fresh demo produces `docs/media/demo.gif` with duress login, Pix of R$ 200, decoy balance R$ 112,47, real balance R$ 48.750,32 and the held Pix on the fraud desk | AC-08, BR-002, BR-008 |
| At 390 px width the panels stack and the page has no horizontal scroll | BR-010 |
| "Saldo insuficiente" and "PIN incorreto" look the same on both phones | BR-004 |
