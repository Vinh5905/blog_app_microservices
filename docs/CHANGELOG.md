# Source remediation history

This is the concise handoff log for implementation work. The architecture source of truth remains `Kien-truc-DevOps-BlogApp copy.md`.

## 2026-09-25 — Added Trivy source and image baseline CI

- Changed: added pinned Trivy source/config PR checks, a five-image post-merge audit and CycloneDX artifacts, a fail-closed exception policy with tests, explicit non-root frontend Docker user, LF checkout rules, and `docs/TRIVY-PRODUCTION-CI.md`.
- Verified: `bash scripts/verify.sh` with JDK 17 passed four independent Maven verifies plus frontend test/build; `docker compose --project-name blogapp-trivy-verify --env-file <temporary-env> up --build -d` made all six containers healthy, and `bash scripts/smoke-test.sh` passed. Local Trivy v0.74.0 scanned five source manifests (244 fixable HIGH/CRITICAL), five Dockerfiles (0 HIGH/CRITICAL), and five built image IDs (186 fixable HIGH/CRITICAL); five CycloneDX SBOMs were generated. Six policy unit tests, `actionlint` on backend CI, and `docker compose config --quiet` passed. The source enforce policy correctly failed on the measured debt.
- Pending: remediate source/image findings, verify actual GitHub Actions runs and exception expiry behavior on PRs, then enable required checks and implement the digest-based GHCR release gate. SonarQube Cloud remains unconfigured.

## 2026-09-25 — Designed SonarQube Cloud CI gate

- Changed: added `docs/SONARQUBE-CLOUD-CI-DESIGN.md` for five monorepo projects, staged backend/frontend Quality Gates, coverage imports, CI checks, token boundaries, failure handling, and acceptance evidence.
- Verified: cross-checked the design against the current architecture, backend workflow, Maven JaCoCo setup, and frontend test gap; documentation whitespace and `git diff --check` passed. No Sonar analysis or GitHub ruleset was run.
- Pending: configure SonarQube Cloud projects, implement CI scans and frontend tests, enable required checks, and collect remote run evidence.

## 2026-09-20 — Updated and automated GitHub Actions pins

- Changed: upgraded backend CI to immutable `actions/checkout` v7.0.1, `actions/setup-java` v6.0.1, and `actions/upload-artifact` v7.0.1 SHAs; pinned both jobs to Ubuntu 24.04; disabled persisted checkout credentials; added weekly Dependabot updates for GitHub Actions.
- Verified: both GitHub YAML files parsed successfully; every workflow `uses:` reference passed the 40-character SHA check; `./scripts/verify.sh` passed all four Maven builds and the frontend production build when run with the local-network/Docker permissions required by WireMock and Testcontainers; GitHub-hosted `Backend CI` run 35504921337 passed all four module jobs, report uploads, and the aggregate test gate.
- Pending: none.

## 2026-09-20 — Expanded backend testing handoff

- Changed: rewrote `docs/BACKEND-TESTING-PLAN.md` as a detailed phase record covering the original baseline, decisions, Maven lifecycle, API/JWT contracts, per-module test matrix, implementation mapping, CI behavior, acceptance evidence, explicit gaps, and ordered follow-up work.
- Verified: cross-checked every listed test and status against the current test sources/POMs; `git diff --check` passed.
- Pending: implement the security/HTTP and gateway-resilience cases marked `Pending` in sections 6 and 12 of the plan.

## 2026-09-20 — Implemented backend production test phase

- Changed: added the backend testing plan; standardized validation/status/error responses; hardened JWT and ownership handling; added the unique-email Flyway migration; added JUnit/Mockito unit tests, MySQL 8.4 Testcontainers HTTP/security tests, Flyway upgrade tests, WireMock gateway route tests, Surefire/Failsafe/JaCoCo gates, and the pinned GitHub Actions backend matrix gate; extended smoke coverage for malformed JWT and cross-user deletion.
- Verified: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin:$PATH ./scripts/verify.sh` passed 31 Java tests across four independent Maven builds plus the frontend production build; auth/post/comment JaCoCo line coverage was 92.26%/93.28%/92.54% and branch coverage was 70.00%/72.22%/72.22%; `docker compose up --build -d` completed with all six containers healthy; `./scripts/smoke-test.sh` passed same-origin auth/post/comment flows, malformed JWT 401, cross-user 403, and owner cleanup.
- Pending: frontend Jest/component and Playwright E2E tests; frontend dependency audit/CRA migration; broader security scanning and trusted image release stages.

## 2026-09-20 — Added implementation roadmap

- Changed: added `docs/NEXT-STEPS.md` with the reviewed project status, milestone gaps, ordered implementation phases, acceptance evidence, and immediate checklist.
- Verified: documentation structure reviewed and `git diff --check` passed.
- Pending: begin Phase 1 by adding backend security/ownership tests, MySQL integration tests, and frontend component tests.

## 2026-09-18 — Runtime baseline aligned with source audit

- Changed: replaced Eureka/Config Server runtime dependencies with explicit service URLs; corrected three gateway routes and same-origin frontend API calls; standardized backend container ports; externalized DB/JWT settings; added Actuator/Prometheus endpoints and graceful shutdown.
- Changed: restored Maven Wrapper metadata; removed unused cross-service executable-JAR dependencies; added isolated H2 context tests; made Flyway migrations database-scoped.
- Changed: invalid JWTs now return 401; post/comment authors come from authenticated JWT identity; delete operations enforce ownership; signup checks the submitted email.
- Changed: added non-root multi-stage images, MySQL/bootstrap Compose stack, independent database users, local verification/smoke scripts, and agent handoff instructions.
- Removed: obsolete `config-server` and `discovery-server` modules plus generated/cache directories after confirming the target runtime contains five modules.
- Fixed during runtime verification: configured the Nginx document root so the SPA no longer loops to HTTP 500; made invalid-JWT responses return JSON HTTP 401 without an error redispatch; disabled Watchman in the repeatable frontend test command.
- Verified: `scripts/verify.sh` completed all four Maven builds/tests plus frontend test/build; Docker built all five application images; MySQL and all five application containers are healthy; `scripts/smoke-test.sh` passed signup, login, post, and comment flows through `http://localhost:3000`; malformed JWT returned HTTP 401.
- Pending: the React/CRA dependency tree reports 71 audit findings and has no component tests; frontend lint and Spring Security DSL deprecation warnings remain. Kubernetes/Helm, CI, Terraform/Ansible, GitOps, and the wider observability stack remain for later architecture milestones.
