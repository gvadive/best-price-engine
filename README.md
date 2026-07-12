# Best Price Engine

A best-offer comparison engine: given a product and buyer criteria (quantity,
delivery window, minimum rating), ranks dummy-retailer offers and surfaces
the cheapest match — including the case where buying a larger quantity from
one retailer is cheaper per unit than the exact quantity from another.

**Educational/portfolio project.** The domain (retailer price comparison) is
a generic, publicly-known pattern — not a reproduction of any employer's
proprietary system. All sample data, retailer names, and figures are
invented for demonstration.

## Architecture

```
                         ┌──────────────┐
   S3 catalog upload ──► │    Lambda    │──► Step Function
                         │  (trigger)   │    ┌─────────────┐
                         └──────────────┘    │  Validate   │
                                              │     ↓       │
                                              │    Load     │──► Ingestion Service
                                              └─────────────┘         │
                                                                       │ REST
   Browser ──► nginx ──┬─► Ingestion Service (Spring Boot, JPA/H2)   │
   (frontend)          │                                              │
                        └─► Pricing Engine (×2, load-balanced) ───────┘
                              │
                              ├─ Resilience4j circuit breaker + retry
                              │  (falls back to cache if Ingestion is down)
                              └─ PriceRanker: filters + sorts offers by
                                 unit price or total cost for a given qty
```

Two independently-deployable Spring Boot services, not a monolith:
**Ingestion Service** owns retailer data, orders, and the two order
variants below; **Pricing Engine** owns the ranking logic and calls
Ingestion over REST, with a circuit breaker and cache fallback so a
slow/down Ingestion Service degrades gracefully instead of failing the
whole comparison.

## Order placement

Every comparison result carries a `priceVersion`. Placing an order quotes
that version back — if the retailer's price has moved since (simulated via
a price-update endpoint), the order is rejected rather than silently
filled at a price the buyer never agreed to. Two fulfillment modes:
`EXACT_QUANTITY` (fill the full amount or nothing) and `BEST_EFFORT` (fill
what's available, cancel the rest).

Two richer order types build on the same mechanism:

- **Bulk pricing request** — for large quantities, request a negotiated
  price (a bigger discount than the public tiers show) instead of the
  standard list price. Short-lived and single-use.
- **Price watch order** — set a target price and how long to wait; fills
  automatically the moment the price drops to that level (or immediately,
  if it already qualifies), and cancels itself automatically if the
  window closes first. Enforced by a real scheduled background job, not
  a one-off timer.

## What's built and verified (not just written)

| Layer | What | Verified how |
|---|---|---|
| Unit | `BestOfferSelector` engine (Python) | 7 pytest cases, all passing |
| Service | Ingestion Service (Spring Boot, JPA/H2) | Real POST/GET round-trip via curl |
| Service | Pricing Engine (Spring Boot, Resilience4j) | Real circuit-breaker test — killed Ingestion mid-run, Pricing Engine kept serving cached results |
| Domain | Order placement, bulk pricing, price watch orders | Every fulfillment/rejection/expiry path exercised with real requests, including a real scheduled-job expiry wait |
| Integration | pytest API + automated resiliency suite | Starts both services itself, seeds data, kills Ingestion, asserts fallback — one command, all passing |
| E2E | Playwright, real Chromium browser | All action-panel flows (order/bulk/watch) driven through the real UI against the real stack |
| Infra | docker-compose: 2× Pricing Engine behind nginx | Real round-robin proven via nginx upstream logs across 20 requests |
| Packaging | Gradle → self-hosted Nexus | Real `./gradlew publish`, artifact confirmed present via Nexus's own API |
| Cloud (local) | S3 → Lambda → Step Function → DynamoDB (via LocalStack) | Real file upload, pipeline auto-triggered, `SUCCEEDED`, data confirmed loaded |
| Cloud (real AWS) | Same pipeline, deployed for real | Real S3 upload → real Lambda → real Step Function `SUCCEEDED` → real DynamoDB, least-privilege IAM (self-escalation correctly blocked and verified), billing alert configured |
| CI | GitHub Actions workflow | YAML validated; full run pending first push |

## Roadmap (explicitly not done yet — not hidden)

- Kubernetes manifests
- Terraform/IaC for the AWS resources
- Monitoring (Prometheus/Grafana via Spring Actuator)
- Non-functional/load testing

## Running it locally

```bash
./gradlew build
docker-compose up -d
# frontend + API through nginx: http://localhost:8080
```

## Stack

Java 17 · Spring Boot 3 · Resilience4j · Gradle (multi-module) · Python 3 ·
pytest · Playwright · Docker / docker-compose · nginx · Nexus (self-hosted) ·
AWS S3/Lambda/Step Functions via LocalStack · GitHub Actions
