# svc-integration-hub

Multi-tenant **TPRM Integration Hub** for G3Sec — connect external systems (Odoo, Custom REST), discover resources, map fields to Vendor Master, and sync inbound on demand or on a schedule.

| | |
|---|---|
| **Stack** | Java 21 · Spring Boot 3.3 · MongoDB · Redis · JWT |
| **Default port** | `8098` |
| **API base** | `/api/v1/integration-hub` |

## Documentation

| Document | Audience |
|---|---|
| **[Full Lifecycle Documentation](./docs/INTEGRATION_HUB_FULL_LIFECYCLE.md)** | Product, BA, architects, engineers, ops — **start here** |
| [Backend Execution Plan (M1/M2)](./docs/TPRM_Integration_Hub_Backend_Execution_Plan.md) | Historical design baseline for Connection + Discovery |
| [Enterprise Guidelines](./docs/CURSOR_ENTERPRISE_GUIDELINES.md) | Engineering / AI agent standards |

## Quick lifecycle

```text
Catalog → Connection (test/activate) → Discovery → Integration mapping
  → Preview → Activate → Sync (manual/scheduled) → Executions → Retry failed records
```

## Run locally

```bash
mvn spring-boot:run
```

Override secrets and infrastructure via environment variables (`MONGODB_URI`, `REDIS_*`, `JWT_SECRET`, `CREDENTIAL_ENCRYPTION_KEY`, etc.). See the lifecycle doc §10.
