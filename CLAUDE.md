# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Build & test (mirrors what CI runs, in order)
```bash
./gradlew build                                     # both Spring services
cd ingestion-service/lambda && pytest -v             # BestOfferSelector unit tests
cd tests && pytest -v                                # API + resiliency suite — starts both services itself, isolated ports
docker compose build ingestion-service pricing-engine-1 pricing-engine-2 nginx
docker compose up -d ingestion-service pricing-engine-1 pricing-engine-2 nginx
cd frontend && pytest test_compare_flow.py test_order_actions_flow.py --browser chromium   # Playwright E2E — needs the stack above running
docker compose down -v
```

### Run a single test
```bash
cd tests && pytest test_order_flow.py::test_name -v
cd ingestion-service/lambda && pytest test_best_offer_selector.py::test_name -v
```

### Frontend dev (bpe-frontend, React/Vite — the live UI)
```bash
cd bpe-frontend
npm run dev     # local dev server
npm run build   # production build (this is what the nginx image bakes in)
npm run lint    # oxlint
```

### Full local stack
```bash
docker-compose up -d
# frontend + API through nginx: http://localhost:8080
```

### LocalStack pipeline (S3 → Lambda → Step Function → DynamoDB)
```bash
docker-compose up -d localstack
bash infra/localstack/setup.sh
```
Real AWS equivalent: `bash infra/aws/deploy.sh`.

### Publish a Java module to the self-hosted Nexus
```bash
./gradlew publish
```

## Architecture

Two independently-deployable Spring Boot services, not a monolith:

- **ingestion-service** (`com.bestpriceengine.ingestion`) — owns all persisted state: `Offer`,
  `Order`, `BulkPricingRequest`, `PriceWatchOrder`, `User`. Spring Security session-auth
  (BCrypt), JPA/H2. `PriceWatchOrder` fills run off a real `@Scheduled` sweep, not a one-off
  timer — GTD expiry is genuine, not simulated.
- **pricing-engine-service** (`com.bestpriceengine.pricing`) — stateless; calls
  ingestion-service over REST via `IngestionClient`, wrapped in a Resilience4j circuit
  breaker + cache fallback. `PriceRanker` does the actual ranking. Two replicas
  (`pricing-engine-1`/`pricing-engine-2`, both built from the same Dockerfile) sit behind
  nginx for load-balancing.
- **bpe-frontend** — React/Vite, the live UI. Built directly into the `nginx` image itself
  (multi-stage Dockerfile: Node build stage → nginx serve stage), not volume-mounted;
  `nginx.conf` is baked in at build time too, so the `nginx` image is fully self-contained
  and pushable to a registry exactly like the two Spring services.
- **frontend/** — NOT the live UI. It's the two Playwright test files
  (`test_compare_flow.py`, `test_order_actions_flow.py`) that drive whatever's actually
  running at `localhost:8080`. The React rewrite deliberately preserved every original
  element ID/class from the old vanilla-JS build specifically so these tests needed zero
  changes — kept that way; don't rename IDs/classes in `bpe-frontend` without checking
  these first.
- **nginx routing** (`nginx/nginx.conf`) is path-prefix based: `/api/compare` →
  pricing-engine, `/api/(auth|offers|products|orders|bulk-pricing-requests|price-watch-orders)`
  → ingestion-service, everything else → the React static build. A new backend endpoint
  needs a route added to that regex or it 404s through nginx even though the service itself
  is fine.
- **infra/localstack** — an S3 upload triggers a Lambda (`validate_catalog.py`) → Step
  Function → `load_to_dynamodb.py`, an async catalog-ingestion pipeline. Runs against
  LocalStack locally (`infra/localstack/setup.sh`); `infra/aws/deploy.sh` runs the identical
  Lambda code against real AWS, switched via the `LOCALSTACK_ENDPOINT` env var.

## CI/CD pipeline shape (`.github/workflows/ci.yml`)

Three jobs, push to `develop` only runs all three; PRs only run the first:
1. **build-test** — builds both Spring services, runs the Python unit + API/resiliency
   suites, then builds and runs the real docker-compose stack and runs Playwright against it.
2. **build-and-push** (push only) — builds all 3 images (ingestion, pricing, frontend) on
   the GitHub Actions runner itself (real RAM, not the ephemeral EC2 box) and pushes to
   **ghcr.io**, deliberately not AWS ECR — `GITHUB_TOKEN` already has registry-push rights,
   so this needs zero new AWS IAM permission.
3. **ec2-smoketest** (push only, needs build-and-push) — launches an ephemeral EC2 instance
   via `infra/aws/deploy-ci-smoketest.sh`, pulls the just-pushed ghcr.io images (no on-box
   compilation), smoke-tests through nginx, and always terminates the instance — cost is
   kept need-only, nothing is left running.

## Known gotchas

- **`order` is a reserved SQL keyword.** The `Order` entity must stay
  `@Table(name = "orders")` — reverting that breaks every generated `INSERT`.
- **Test port collisions fail silently, not loudly.** `tests/conftest.py`'s fixtures must
  use `18081`/`18082`, never `8081`/`8082` (docker-compose's ports) — if both are up at
  once, the fixture's own service silently fails to bind and every test ends up hitting the
  already-running Docker container instead, passing green while proving nothing.
- **nginx serves the static frontend before its Java backend is ready.** Any readiness
  check must poll `ingestion-service` (port 8081) directly, not just nginx (8080) — polling
  nginx alone caused a real CI failure (502s/connection resets on the seed calls).
- **`[hidden]` can be silently overridden by component CSS.** A component's own
  `display: flex` rule beat the `[hidden]` attribute once during the React migration (ID
  selector beats the base stylesheet) — there's a global `[hidden] { display: none !important; }`
  rule guarding against this now; don't remove it without checking the action-panel toggle
  behaviour.

## Git workflow

- Every commit message must reference a GitHub issue (`Refs #N` / `Closes #N`) —
  enforced locally by `scripts/git-hooks/commit-msg` (`core.hooksPath = scripts/git-hooks`).
  A commit without one is rejected before it's even created; check `gh issue list` first.
- `git commit`/`git push` are frequently denied at the Claude Code permission-prompt layer
  in this environment — don't retry repeatedly; hand the exact command to the user's own
  Terminal instead. `gh` (issues/PRs/read-only operations) is not affected by this and
  should be preferred wherever it can do the job.

## AWS conventions

- IAM is least-privilege by design: the deploy user (`best-price-engine-cli`) cannot grant
  itself new permissions — this is AWS's own self-escalation protection, not a limitation
  to route around. Any new permission needs one manual step in the AWS Console.
- All AWS resources are tagged `Project=best-price-engine`.
- EC2 is always ephemeral — launched, smoke-tested, torn down, never left running.

## Local environment (this Mac)

- Docker runs via **Colima**, not Docker Desktop — `colima start --memory 4`. If `docker`
  commands hang while `colima status` reports "running", try a plain `colima restart`
  first (`--memory` is a `start`-only flag, not valid on `restart`, but the original
  allocation persists through it).
- Both Spring services target JDK 17 (`build.gradle`'s toolchain) — a locally-usable JDK 17
  install is required even if it's owned by a different/stale uid.
- `gh` (GitHub CLI) is installed standalone at `~/.local/bin/gh`, authenticated via device
  flow — not managed by Homebrew.
