# G3Sec TPRM Integration Hub — Full Lifecycle Documentation

| Field | Value |
|---|---|
| **Service** | `svc-integration-hub` |
| **Package** | `com.g3cs.integration` |
| **Default port** | `8098` |
| **API base** | `/api/v1/integration-hub` |
| **Stack** | Java 21 · Spring Boot 3.3 · MongoDB · Redis · JWT |
| **Document role** | Product · Business · Architecture · Engineering · Operations |
| **Source of truth** | Running codebase (controllers, services, SPI). The older M1/M2 execution plan remains historical design intent. |
| **Related** | [Backend Execution Plan (M1/M2)](./TPRM_Integration_Hub_Backend_Execution_Plan.md) · [Enterprise Guidelines](./CURSOR_ENTERPRISE_GUIDELINES.md) |

---

## 1. Executive summary

The **TPRM Integration Hub** is a multi-tenant backend service that connects G3Sec’s Third-Party Risk Management platform to external business systems (Odoo first; Custom REST second). It lets tenants:

1. Register a remote system instance as a **Connection**
2. Discover remote resources and fields
3. Define an **Integration** that maps remote data onto TPRM modules (today: **Vendor Master**)
4. Sync inbound on demand or on a schedule
5. Inspect executions and retry failed records

**One-line mental model:**  
*Per-tenant connection catalog + pluggable connectors feed a scheduled/manual inbound sync engine that maps remote records into G3Sec `vendor_master`, with Redis locks, encrypted credentials, and failed-record retry.*

---

## 2. Business context & value

### 2.1 Problem

TPRM teams maintain vendor risk data inside G3Sec, while vendor master data often already lives in ERP/CRM systems (e.g. Odoo). Manual re-entry is slow, error-prone, and drifts out of date.

### 2.2 Solution

A hub that:

| Capability | Business outcome |
|---|---|
| Catalog of applications & connectors | Standardize “what systems we support” |
| Connection lifecycle | Securely attach a tenant’s specific Odoo/REST instance |
| Live discovery | Mapping UI without hardcoding remote models |
| Field mapping + preview | Business-controlled transform before go-live |
| Inbound sync (manual / periodic) | Keep Vendor Master current |
| Executions + failed-record retry | Operable, recoverable syncs |

### 2.3 Personas & use cases

| Persona | Goals |
|---|---|
| **Tenant admin / integration owner** | Connect Odoo, map Contacts → Vendor Master, activate sync |
| **TPRM analyst** | Trust synced vendor records for assessments |
| **Frontend engineer** | Consume catalog, connection, discovery, integration APIs |
| **Platform / ops** | Multi-tenant isolation, credentials, scheduler health |
| **Connector developer** | Add a new `RemoteConnector` without changing the sync engine |

**Primary happy-path use case**

1. Seed/list Applications & Connectors  
2. Create Connection (HTTPS base URL + credentials)  
3. Test → Activate  
4. Discover resources / fields / sample  
5. Create Integration (remote resource → `vendor_master`)  
6. Save mapping → Preview → Activate  
7. Sync (manual or scheduled)  
8. Review executions; retry failed records  

---

## 3. Domain model (four locked concepts)

These four concepts must never be collapsed into one document:

| Concept | Business question | Mongo collection | Public ID pattern |
|---|---|---|---|
| **Application** | What system is this? (Odoo, Custom REST…) | `applications` | `APP-…` |
| **Connector** | What can this implementation do? | `connectors` | `CON-…` |
| **Connection** | How do I reach *this tenant’s* instance? | `connection_configurations` (+ `connection_credentials`) | `CON-…` / connection id |
| **Integration** | What business data do I exchange, and how mapped? | `integrations` | `INT-…` |

Supporting runtime entities:

| Entity | Collection | Purpose |
|---|---|---|
| Execution | `integration_executions` | One sync run + counters |
| Failed record | `integration_failed_records` | Per-row failure / retry |
| Identity map | `entity_identity_map` | External ID ↔ TPRM record |
| Vendor Master | `vendor_master` | Sync target (TPRM) |
| Audit | `stage_audit_logs` | Lifecycle audit trail |
| Sequences | `sequence_master` / `sequence_counter` | Public ID generation |
| Tenant directory | `tenant_management` (platform DB) | Scheduler tenant list |

---

## 4. Solution architecture

### 4.1 Logical architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     G3Sec Platform (JWT / Sessions)             │
└───────────────────────────────┬─────────────────────────────────┘
                                │ Bearer JWT
┌───────────────────────────────▼─────────────────────────────────┐
│                    svc-integration-hub (:8098)                  │
│  Controllers → Services → SPI / Sync Engine                     │
│                                                                 │
│  Catalog │ Connection │ Integration │ Scheduler │ Audit         │
│                    │                                            │
│              ConnectorRegistry                                  │
│           ┌────────┴────────┐                                   │
│     OdooConnector    CustomRestConnector                        │
└─────┬───────────────┬───────────────┬───────────────────────────┘
      │               │               │
      ▼               ▼               ▼
  MongoDB         Redis           External systems
  (tenant DBs     (locks +        (Odoo JSON-2,
   + master DB)    cache)          Custom REST, OAuth2)
```

### 4.2 Design principles (as implemented)

| Principle | How it shows up |
|---|---|
| **SPI / Dependency Inversion** | Sync engine and discovery call `RemoteConnector`, never Odoo-specific types |
| **Multi-tenancy** | JWT → `TenantContext` → `TenantAwareMongoDatabaseFactory` routes DB |
| **Secrets isolation** | Credentials AES-GCM encrypted in `connection_credentials`; API masks references |
| **Durable config vs rebuildable metadata** | Connections/integrations persist; discovery is live (Redis TTLs configured for future cache) |
| **Inbound-first** | Sync path is remote read → transform → upsert Vendor Master |
| **Operability** | Executions, failed records, manual retry |

### 4.3 Tech stack

| Layer | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.12 |
| API | Spring MVC REST |
| Persistence | MongoDB (Spring Data / `MongoTemplate`) |
| Cache & locks | Redis (Lettuce) |
| Security | Spring Security + custom JWT `AuthFilter` (JJWT + AES-GCM claim decrypt) |
| HTTP outbound | Spring `RestClient` |
| Scheduling | Spring `@Scheduled` |
| Mapping (declared) | MapStruct (where used) |

### 4.4 Package map

```
com.g3cs.integration/
  IntegrationApplication.java          # Boot + @EnableScheduling
  catalog/                             # TPRM field catalog
  common/                              # DTOs, enums, exceptions, messages
  config/                              # Mongo, Redis, Security, RestClient
  controller/                          # REST surface
  dto/                                 # Request bodies
  model/                               # Mongo documents
  repository/                          # Sequence custom repos
  scheduler/                           # Cross-tenant sync poller
  service/ (+ impl/)                   # Business logic
  spi/ (+ odoo/, custom/)              # Connector contract + adapters
  tenant/                              # ThreadLocal + DB routing
  utils/                               # Auth, JWT, AES, URL helpers
```

---

## 5. End-to-end product lifecycle

This is the full business + technical lifecycle of an integration, from catalog to ongoing operations.

```
  ┌──────────┐   ┌────────────┐   ┌───────────┐   ┌──────────┐   ┌─────────┐
  │ 1.Catalog│ → │2.Connection│ → │3.Discovery│ → │4.Mapping │ → │5.Activate│
  └──────────┘   └────────────┘   └───────────┘   └──────────┘   └────┬────┘
                                                                       │
                     ┌─────────────────────────────────────────────────┘
                     ▼
              ┌────────────┐   ┌────────────┐   ┌────────────┐
              │ 6.Execute  │ → │7.Monitor   │ → │8.Remediate │
              │  Sync      │   │ Executions │   │ Retry DLQ  │
              └────────────┘   └────────────┘   └────────────┘
```

### Stage 1 — Catalog (platform foundation)

**Purpose:** Define which external systems and connector implementations exist.

| API | Action |
|---|---|
| `GET /applications` | List applications (`APP-ODOO`, `APP-CUSTOM`, …) |
| `GET /connectors` | List connectors (`CON-ODOO-JSON2`, `CON-CUSTOM-REST`, …) |
| `GET /tprm-modules` | List TPRM target modules |
| `GET /tprm-modules/{moduleKey}/fields` | Target field definitions for mapping UI |

Seeded on startup / seed service (idempotent): applications, connectors, indexes, sequences.

**TPRM target today:** only `vendor_master`.

Required Vendor Master mapping fields for activation: `vendorName`, `entityName` (plus optional resolve fields — see §8).

### Stage 2 — Connection lifecycle

**Purpose:** Bind a tenant to a concrete remote instance with encrypted credentials.

#### Connection status state machine

```
                 create
                   │
                   ▼
                ┌──────┐
         ┌─────►│DRAFT │◄──── update (resets)
         │      └──┬───┘
         │         │ test / activate path
         │         ▼
         │   ┌──────────┐     auth failure
         │   │ TESTING* │──────────────────► AUTH_EXPIRED
         │   └────┬─────┘
         │        │ activate (successful test)
         │        ▼
         │   ┌────────┐     soft issues
         │   │ ACTIVE │───────────────────► DEGRADED
         │   └───┬────┘
         │       │ disable / delete
         │       ▼
         │  ┌──────────┐
         └──│ DISABLED │
            └──────────┘

* TESTING may be transient during test/activate flows.
```

| Status | Meaning |
|---|---|
| `DRAFT` | Configured but not live for sync |
| `TESTING` | Connectivity check in progress / transitional |
| `ACTIVE` | Eligible for discovery + sync |
| `DEGRADED` | Reachable but unhealthy / partial |
| `AUTH_EXPIRED` | Credentials rejected |
| `DISABLED` | Soft-disabled or deleted path |

#### Connection APIs

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/connections` | Create (`DRAFT`); encrypt & store credentials |
| `PUT` | `/connections/{id}` | Update; typically returns to `DRAFT` |
| `GET` | `/connections/{id}` | Get one (credentials masked) |
| `GET` | `/connections` | List for tenant |
| `POST` | `/connections/{id}/test` | Live connectivity/auth check; updates health |
| `POST` | `/connections/{id}/activate` | Test then set `ACTIVE` |
| `POST` | `/connections/{id}/disable` | Set `DISABLED` |
| `DELETE` | `/connections/{id}` | Soft-delete / disable path |

**Rules of note**

- Base URL must be **HTTPS**
- Secrets live in `connection_credentials` (AES-GCM); responses mask credential references
- Sync requires connection **ACTIVE**

### Stage 3 — Discovery

**Purpose:** Power the mapping UI from live remote metadata (not hardcoded Odoo model lists).

| Method | Path | Returns |
|---|---|---|
| `GET` | `/connections/{id}/resources` | Discoverable remote resources |
| `GET` | `/connections/{id}/resources/{resourceKey}/fields` | Field schema for a resource |
| `GET` | `/connections/{id}/resources/{resourceKey}/sample` | Sample rows (limit from config, default 5) |

Discovery is delegated through `ConnectorRegistry` → `RemoteConnector.discoverResources` / `discoverFields` / sample via `searchRead`.

**Config (intended cache TTLs):** `integration.discovery.*` in `application.yaml` (resource/field/capability TTLs). Treat discovery as rebuildable metadata.

### Stage 4 — Integration definition & mapping

**Purpose:** Declare *what* to sync and *how* each remote field maps to TPRM.

#### Integration status state machine

```
   create / update
         │
         ▼
      ┌──────┐
      │DRAFT │── mapping incomplete ── (stay DRAFT)
      └──┬───┘
         │ activate (required fields mapped)
         ▼
      ┌────────┐
      │ ACTIVE │◄──► PAUSED* / deactivate → DISABLED / DRAFT paths
      └────────┘

* PAUSED is supported as a status; primary control APIs are activate / deactivate.
```

| Status | Meaning |
|---|---|
| `DRAFT` | Editable; not scheduled |
| `ACTIVE` | Eligible for manual + scheduled sync |
| `PAUSED` | Temporarily not running |
| `DISABLED` | Turned off |

#### Sync configuration (on integration)

| Dimension | Values / notes |
|---|---|
| Direction | `INBOUND` (exercised), `OUTBOUND`, `BIDIRECTIONAL` (enums present) |
| Mode | `MANUAL`, `PERIODIC`, `SCHEDULED` |
| Conflict policy | `TPRM_WINS`, `REMOTE_WINS`, `LATEST_WINS` |
| Cursors | `last_write_date`, `last_offset`, `next_run_at` |

#### Integration APIs

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/integrations` | Create integration |
| `PUT` | `/integrations/{id}` | Update definition |
| `GET` | `/integrations/{id}` | Get one |
| `GET` | `/integrations` | List |
| `POST` | `/integrations/{id}/mapping` | Save field mappings |
| `POST` | `/integrations/{id}/preview` | Transform sample without persist |
| `POST` | `/integrations/{id}/activate` | Validate required mappings → `ACTIVE` |
| `POST` | `/integrations/{id}/deactivate` | Take offline |

### Stage 5 — Activate (go-live gate)

Activation is a product control gate, not just a flag:

1. Connection must be usable (`ACTIVE` for sync)
2. Integration mapping must include required Vendor Master fields
3. On activate, scheduler may set / honor `next_run_at` for periodic modes

### Stage 6 — Execute sync

**Triggers**

| Trigger | Entry |
|---|---|
| Manual | `POST /integrations/{id}/sync` |
| Scheduled | `IntegrationSyncScheduler` (default cron every minute) |

**Scheduler behavior (high level)**

1. Load active tenants from platform `tenant_management`
2. For each tenant, bind `TenantContext`
3. Find integrations due (`next_run_at <= now`, status `ACTIVE`)
4. Run sync engine under Redis distributed lock

**Sync engine pipeline**

```
Acquire Redis lock: integration:lock:{tenant}:{integrationId}
        │
        ▼
Resolve Connector + ACTIVE Connection credentials
        │
        ▼
Paginated RemoteConnector.searchRead
  (Odoo: optional incremental write_date domain)
        │
        ▼
For each record:
  TransformEngine (mapping)
  MasterValueResolver (entity / param / master → IDs, Redis-cached)
  VendorPersistService upsert + entity_identity_map
        │
        ▼
Write IntegrationExecution (SUCCESS | PARTIAL | FAILED)
Persist IntegrationFailedRecord for bad rows
Update cursors: last_write_date / last_offset / next_run_at
Release lock
```

| Execution status | Meaning |
|---|---|
| `RUNNING` | In progress |
| `SUCCESS` | All processed rows OK |
| `PARTIAL` | Some rows failed |
| `FAILED` | Run-level failure |

**Sync knobs** (`integration.sync.*`)

| Setting | Default intent |
|---|---|
| `default-batch-size` | 100 |
| `max-batch-size` | 500 |
| `max-rows-without-pagination` | 2000 (guardrail) |
| `lock-ttl-seconds` | 900 |

### Stage 7 — Monitor

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/integrations/{id}/executions` | Run history / stats |
| `GET` | `/integrations/{id}/failed-records` | Failed row queue |
| `GET` | `/health` (open) | Service liveness |
| Audit | `stage_audit_logs` | Connection/integration stage events |

### Stage 8 — Remediate (failed-record lifecycle)

| Failed record status | Intent |
|---|---|
| Pending / failed states | Stored with `payloadSnapshot` |
| Retried | Replayed through transform + persist |
| Resolved | After successful retry |

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/integrations/{id}/failed-records/retry` | Retry selected |
| `POST` | `/integrations/{id}/failed-records/retry-all` | Retry queue |

There is **no** automatic exponential backoff / message-queue consumer today — remediation is API-driven.

---

## 6. Connector SPI & adapters

### 6.1 Contract (`RemoteConnector`)

```text
connectorId()
test(context)
discoverResources(context, filter)
discoverFields(context, resourceKey)
searchRead(context, request)     → inbound pages
create(context, resourceKey, values)
write(context, resourceKey, externalId, values)
```

Registered via Spring beans into `ConnectorRegistry` keyed by `connectorId`.

### 6.2 Implemented connectors

| Connector ID | Adapter | Auth / protocol notes |
|---|---|---|
| `CON-ODOO-JSON2` | `OdooConnector` + `OdooJson2Client` | Odoo JSON-2 (`/json/2/{model}/{method}`), `X-Odoo-Database`, incremental `write_date` |
| `CON-CUSTOM-REST` | `CustomRestConnector` | Configurable paths (`testPath`, `discoveryPath`, `fieldsPath`, `resourcePrefix`) |

### 6.3 Outbound authentication (`AuthApplicator`)

Supported types: `API_KEY`, `BEARER`, `BASIC`, `OAUTH2` (client credentials), `CUSTOM_HEADER`, `NONE`.

### 6.4 Capabilities (enum / catalog)

Readable discovery + sync are live. `WEBHOOK` appears as a capability concept but **inbound webhooks are not implemented**. Kafka / Redis Streams / Outbox from the older roadmap are **not** in the runtime path.

---

## 7. Security, tenancy & compliance posture

### 7.1 Authentication

1. Client sends `Authorization: Bearer <JWT>`
2. `AuthFilter` verifies HS256 signature (`JWT_SECRET`)
3. Decrypts AES-GCM `enc` claim → `userId`, `tenantId`, `tenantDb`
4. Validates session against `user_details.session` (active token + expiry), aligned with `tprm-core`
5. Open endpoints: `/health`, `/api/v1/integration-hub/health`

### 7.2 Multi-tenancy

| Mechanism | Role |
|---|---|
| `TenantContext` (ThreadLocal) | Request / scheduler identity |
| `TenantAwareMongoDatabaseFactory` | Routes Mongo calls to tenant DB (or master for platform) |
| Document `tenant_id` filters | Defense in depth on lists/queries |
| `TenantDirectoryService` | Scheduler enumerates active tenants from platform DB |

### 7.3 Secrets

| Concern | Approach |
|---|---|
| Connection credentials | AES-GCM (`CREDENTIAL_ENCRYPTION_KEY`) |
| API responses | Mask credential references |
| Transport to remote | HTTPS required on connection base URL |

### 7.4 CORS

Configured for localhost and `*.g3sec.ai` origins (see `SecurityConfig`).

---

## 8. TPRM target: Vendor Master mapping

Module key: `vendor_master` → collection `vendor_master`.

| Field key | Label | Required | Resolve kind |
|---|---|---|---|
| `vendorName` | Vendor name | Yes | — |
| `entityName` | Entity | Yes | `RESOLVE_ENTITY` |
| `companyWebsite` | Company website | No | — |
| `email` | Email | No | — |
| `phoneNo` | Phone | No | — |
| `address` | Address | No | — |
| `location` | Location | No | — |
| `vendorCategory` | Vendor category | No | `RESOLVE_PARAM` |
| `businessUnit` | Business unit | No | `RESOLVE_PARAM` |
| `natureOfEngagement` | Nature of engagement | No | `RESOLVE_PARAM` |
| `businessFunctions` | Business functions | No | `RESOLVE_PARAM` |
| `status` | Status | No | `RESOLVE_MASTER` |
| `remark` | Remark | No | — |

Resolve kinds use tenant lookup collections (`entity_details`, `business_param_dtl`, `master_data`, etc.) with Redis short-lived caching via `MasterValueResolver`.

Identity continuity: `entity_identity_map` links remote external IDs to TPRM vendor records for upserts.

---

## 9. API catalog (as implemented)

Base path: `/api/v1/integration-hub`

### Health

| Method | Path | Auth |
|---|---|---|
| `GET` | `/health` | Open |

### Catalog

| Method | Path |
|---|---|
| `GET` | `/applications` |
| `GET` | `/connectors` |
| `GET` | `/tprm-modules` |
| `GET` | `/tprm-modules/{moduleKey}/fields` |

### Connections

| Method | Path |
|---|---|
| `POST` | `/connections` |
| `PUT` | `/connections/{connectionId}` |
| `GET` | `/connections/{connectionId}` |
| `GET` | `/connections` |
| `POST` | `/connections/{connectionId}/test` |
| `POST` | `/connections/{connectionId}/activate` |
| `POST` | `/connections/{connectionId}/disable` |
| `DELETE` | `/connections/{connectionId}` |
| `GET` | `/connections/{connectionId}/resources` |
| `GET` | `/connections/{connectionId}/resources/{resourceKey}/fields` |
| `GET` | `/connections/{connectionId}/resources/{resourceKey}/sample` |

### Integrations

| Method | Path |
|---|---|
| `POST` | `/integrations` |
| `PUT` | `/integrations/{integrationId}` |
| `GET` | `/integrations/{integrationId}` |
| `GET` | `/integrations` |
| `POST` | `/integrations/{integrationId}/mapping` |
| `POST` | `/integrations/{integrationId}/preview` |
| `POST` | `/integrations/{integrationId}/activate` |
| `POST` | `/integrations/{integrationId}/deactivate` |
| `POST` | `/integrations/{integrationId}/sync` |
| `GET` | `/integrations/{integrationId}/executions` |
| `GET` | `/integrations/{integrationId}/failed-records` |
| `POST` | `/integrations/{integrationId}/failed-records/retry` |
| `POST` | `/integrations/{integrationId}/failed-records/retry-all` |

**Response envelope (runtime):** `ApiResponseDto` with `status` / `message` / `code` / `data` (and field errors where applicable). Errors flow through `BusinessException` → `GlobalExceptionHandler`.

---

## 10. Configuration & local run

### 10.1 Environment variables (preferred)

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Profile (default `local`) |
| `MONGODB_URI` / `MONGODB_DATABASE` | Mongo |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` | Redis |
| `JWT_SECRET` | JWT verify + claim decrypt |
| `PLATFORM_MASTER_DB` | Platform master DB name |
| `CREDENTIAL_ENCRYPTION_KEY` | Credential AES key |
| `SERVER_PORT` | Default `8098` |
| `INTEGRATION_SCHEDULER_ENABLED` | Default `true` |
| `INTEGRATION_SCHEDULER_CRON` | Default every minute |
| `COMMUNICATION_SERVICE_URL` | Reserved; not wired in sync path yet |

> Do not commit real secrets. Override all defaults via environment in every non-local environment.

### 10.2 Run

```bash
mvn spring-boot:run
```

Service binds `0.0.0.0:8098` by default.

---

## 11. Observability & operations

| Area | Current state |
|---|---|
| Logging | SLF4J (scheduler + exception paths) |
| Health | Simple `UP` endpoint |
| Audit | Mongo `stage_audit_logs` |
| Metrics / tracing | No Actuator / Micrometer / OTel wired |
| CI / Docker / K8s | Not present in-repo |
| Automated tests | No meaningful `src/test` suite yet |

**Ops checklist for a healthy tenant integration**

1. Connection `ACTIVE` + `HEALTHY` after test  
2. Integration `ACTIVE` with required mappings  
3. Redis reachable (locks)  
4. Scheduler enabled (if not manual-only)  
5. Executions show `SUCCESS` / investigate `PARTIAL` via failed-records  
6. Credentials rotated via connection update when auth expires  

---

## 12. Roadmap vs reality

| Milestone (original plan) | Status in codebase |
|---|---|
| M1 Connection foundation | **Done** (CRUD, test, activate/disable; some plan endpoints like rotate-credential / per-connection health differ) |
| M2 Discovery | **Done** (resources/fields/sample; operations / manual register / full capability APIs incomplete vs plan) |
| M3 Mapping / preview / publish | **Done** (mapping, preview, activate) |
| M4 Execution / retry | **Done** (sync engine, executions, failed-record retry) — without Redis Streams/Outbox |
| M5 Webhooks | **Not implemented** |
| M6 Generic REST | **Done** as `CustomRestConnector` |
| M7 Hardening / load test / OpenAPI | **Open** |

Treat this lifecycle document as the **as-built** product contract. Keep the M1/M2 execution plan as design history and decision trail.

---

## 13. Glossary

| Term | Definition |
|---|---|
| **TPRM** | Third-Party Risk Management |
| **Application** | Catalog entry for an external product family |
| **Connector** | Implementation that talks to an application protocol |
| **Connection** | Tenant-specific instance + credentials |
| **Integration** | Sync job: resource + mapping + schedule |
| **SPI** | Service Provider Interface (`RemoteConnector`) |
| **Identity map** | Link between remote ID and TPRM record |
| **Failed record** | DLQ-like per-row failure with payload snapshot |
| **Tenant DB** | Mongo database selected from JWT / tenant directory |

---

## 14. Document ownership

| Audience | Sections to start with |
|---|---|
| Product / BA | §1–2, §5, §12 |
| Solution architect | §3–4, §6–7, §12 |
| Backend engineer | §4.4, §5–6, §8–10 |
| Frontend engineer | §5, §8–9 |
| Ops / support | §5 stages 6–8, §10–11 |

**Change control:** Material changes to domain separation, sync semantics, security, or multi-tenancy require human sign-off per enterprise guidelines.
