# Best Price Engine

A best-offer comparison engine: given a product and buyer criteria (quantity,
delivery window, minimum rating), ranks dummy-retailer offers and surfaces
the cheapest match — including the case where buying a larger quantity from
one retailer is cheaper per unit than the exact quantity from another.

**Educational/portfolio project.** The domain (retailer price comparison) is
a generic, publicly-known pattern. All sample data, retailer names, and
figures are invented for demonstration.

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
| Frontend | React/Vite (Login, Register, Compare, Order History) | Every original element ID/class preserved from the prior vanilla-JS build, so the full Playwright suite ran unchanged against it |
| E2E | Playwright, real Chromium browser | All action-panel flows (order/bulk/watch) driven through the real UI against the real stack |
| Infra | docker-compose: 2× Pricing Engine behind nginx | Real round-robin proven via nginx upstream logs across 20 requests |
| Packaging | Gradle → self-hosted Nexus | Real `./gradlew publish`, artifact confirmed present via Nexus's own API |
| Cloud (local, iteration 1) | S3 → Lambda → Step Function → DynamoDB (via LocalStack) | Real file upload, pipeline auto-triggered, `SUCCEEDED`, data confirmed loaded — proved the pipeline logic locally, at zero cost, before touching real infrastructure |
| Cloud (real AWS, iteration 2) | Same pipeline, promoted to real infrastructure once proven locally | Real S3 upload → real Lambda → real Step Function `SUCCEEDED` → real DynamoDB, least-privilege IAM (self-escalation correctly blocked and verified), billing alert configured |
| Cloud (EC2, on demand) | Ephemeral EC2 smoke-test, launched by CI | Real `t3.micro` launched, full docker-compose stack deployed, smoke-tested through nginx, then always terminated — never left running, to keep cost on a need basis |
| CI | GitHub Actions: build/test, then EC2 smoke-test | Both jobs run green on real GitHub infra (build-test + ec2-smoketest), the EC2 job completing in ~5 minutes end to end |
| Delivery | GitHub issues tracking each feature | Work tracked and shipped issue-by-issue (e.g. auth + React rewrite as issue #9), commits reference the issue they close |

## How this was built — agentic coding, verified not trusted

This repo was built AI-assisted, with Claude Code writing and running most of the code
directly while I directed the architecture, reviewed every change, and refused to accept
"it should work" as an answer. The discipline that actually matters isn't that the AI wrote
code — it's that nothing was believed until it was run for real, and every failure was
root-caused before being called fixed. A few of the real bugs this caught, exactly as they
happened:

- **A table named `order` broke every generated `INSERT`.** `order` is a reserved SQL
  keyword — it silently collided with `ORDER BY` until the tests were actually run and threw
  real 500 errors. Fixed with an explicit `@Table(name = "orders")`, not a hunch.
- **A passing test suite was quietly testing the wrong thing.** The pytest fixtures used
  ports `8081`/`8082` — identical to docker-compose's ports. When both were running at once
  (normal during iterative development), the fixture's own service silently failed to bind
  and every test accidentally hit the already-running Docker container instead, masking real
  test isolation. The tests were green the whole time. Caught by noticing the coincidence,
  not by a failure — fixed by moving to dedicated ports (`18081`/`18082`) with a fail-loud
  check if the port's already taken.
- **A Lambda that worked from the host machine failed identically inside its own
  container.** `LOCALSTACK_ENDPOINT=localhost:4566` only resolves from the host — a Lambda
  running in a sibling Docker container needs the Docker-network service name
  (`http://localstack:4566`) instead. Same class of networking bug that shows up constantly
  in containerised systems, caught by actually invoking the pipeline, not by reading the code.
- **A CSS rule silently broke the React migration's UI state.** Porting the frontend to React
  kept every original element ID/class so the existing Playwright suite needed zero rewrites
  — but a component's own `display: flex` rule was overriding the `[hidden]` attribute, so
  all three action panels rendered stacked on top of each other at once. Only visible in an
  actual screenshot, not in the DOM tree.
- **AWS correctly refused to let the deploy user grant itself more permission.** Asked
  directly why I didn't just widen the IAM policy myself: AWS's own platform-level
  self-escalation protection means a scoped IAM identity can never grant itself new rights —
  every policy change in this project required one manual step in the AWS Console, by design,
  not a limitation to route around.

None of this is "AI got it right first try." It's closer to the opposite: a fast build loop
where mistakes surfaced quickly *because* everything was actually executed — real curl calls,
real Docker containers, real AWS resources — and every fix was re-verified the same way
before moving on.

## Roadmap

The plan was: a React web app, backend split into independent microservices, the frontend
proven with Playwright, the backend proven with pytest, and delivery tracked issue-by-issue
on GitHub through to a real deploy. That's built and verified — see the table above.

What's left, as infrastructure hardening rather than new features:

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

Java 17 · Spring Boot 3 · Resilience4j · Gradle (multi-module) · React / Vite ·
Python 3 · pytest · Playwright · Docker / docker-compose · nginx ·
Nexus (self-hosted) · AWS (S3, Lambda, Step Functions, DynamoDB via LocalStack
and for real; EC2 for ephemeral, on-demand CI deploys) · GitHub Actions
