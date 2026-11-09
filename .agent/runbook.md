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
```bash
mvn -B verify      # what CI runs on push to main and on pull requests (.github/workflows/ci.yml)
```
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
