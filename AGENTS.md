# BlogApp agent handoff

## Read first

1. Read `docs/Kien-truc-DevOps-BlogApp copy.md` before changing architecture.
2. Always reread sections 4 (source audit), 6 (application baseline), and 17 (acceptance milestones).
3. Read `docs/CHANGELOG.md` to learn what is complete, pending, and already verified.

## Current runtime boundary

The application runtime has five modules: `blog-client`, `api-gateway-server`, `userauthservice`, `postservice`, and `commentservice`. Eureka and Spring Cloud Config were removed after migration to service DNS/environment configuration. Do not reintroduce them without recording an architecture decision.

Local verification uses `compose.yaml`; the public entry point is `http://localhost:3000`, and browser API traffic must remain same-origin under `/api`. Never commit `.env`, credentials, generated `target`, `node_modules`, or frontend `build` output.

## Required workflow

- Preserve independent builds: each Java service must pass its own `./mvnw clean verify` on a clean machine.
- Run `scripts/verify.sh` after source/config changes.
- For runtime changes, run `docker compose up --build -d`, wait for healthy services, then run `scripts/smoke-test.sh`.
- Check invalid JWT handling and ownership rules when touching security or controllers.
- Keep secrets in environment/secret stores; committed files may contain variable names and safe examples only.

## Handoff log format

Append one short entry to `docs/CHANGELOG.md` for each meaningful batch:

```text
## YYYY-MM-DD — short title
- Changed: files/components and behavior.
- Verified: exact commands and outcome.
- Pending: only unfinished work or `none`.
```

Keep entries factual and concise. Do not mark a deployment, test, or acceptance criterion complete without evidence from an actual run.
