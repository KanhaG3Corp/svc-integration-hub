# TPRM Integration Hub — Backend Implementation & Execution Plan
### Module: Connection Foundation + Discovery + Odoo Connector (V1, Custom-Ready)

Status: **Locked / Fixed baseline** — derived from `Backend_Impl_Main_Plan.docx`, `MongoDB_Data_Model_TPRMS_Integration_Hub.docx`, `Odoo_JSON2_API_Integration_Catalog.xlsx`, and `integration-module-v2.html`.
Stack assumed (confirm before coding if different): **Java 17+ / Spring Boot, MongoDB, Redis (cache + streams), Secret Manager for credentials.**

> This document is the single source of truth for the backend build of Milestone 1 (Connection Foundation) and Milestone 2 (Discovery), with the Odoo Connector as the first concrete implementation, designed so a second connector (Generic REST/Custom) drops in without touching core engine code.

---

## 0. Guidelines & Policy (Non-Negotiable)

- Enterprise SaaS application — build for scale from day one, not as a prototype.
- Code must be clean, clear, simple, and easy to understand. **No cleverness for its own sake.**
- Follow **SOLID** principles strictly, especially Single Responsibility and Dependency Inversion (core engine depends on the `Connector` interface, never on `OdooConnector` directly).
- Do **not** over-engineer. No speculative interfaces, no unused abstractions, no extra layers "for the future." Build only what Milestone 1 + Milestone 2 + Odoo Connector need.
- Follow the **existing project structure and conventions** from the sibling TPRMS service that will be opened in this workspace. This document's package layout (Section 3) is the proposed default **only until that reference is available** — align naming/package roots to match the existing service once opened.
- Every file, class, method, and variable name must be self-explanatory — a new developer should understand purpose without needing external docs.
- Every important code block gets a comment explaining **what** it does and **why** it's needed (not restating the obvious).
- Enterprise coding standards: constructor injection only, no field injection, no static mutable state, no `System.out.println`, structured logging with correlation/trace IDs, no swallowed exceptions.
- Scalable and maintainable: new connectors, new resources, new transformations must be addable without modifying the core engine.
- Performance is a top priority:
  - Minimize DB round-trips — batch reads, projection-only queries (fetch only required fields), no N+1 lookups.
  - Minimize external API calls — cache discovery/capability data in Redis, never re-discover on every request.
  - Avoid unnecessary loops/object creation in hot paths (execution engine, mapping engine).
  - Prefer streaming/pagination over loading full result sets into memory.
- Any major architecture, business logic, DB schema, security, or implementation change **must be confirmed by a human before proceeding** — this plan calls out every such decision point explicitly (see Section 9, "Decisions Requiring Sign-Off").
- No assumptions on major implementation decisions — flagged decisions are placeholders and must be confirmed, not silently implemented.
- Prefer simple, optimized, readable, maintainable solutions over complex ones.
- Do not break or regress existing functionality in the sibling TPRMS service.
- All Mongo query logic lives in **Repository / Custom Repository** classes only — **never inline queries inside service/business logic.** Reusable query templates are centralized (Section 6.4).
- Every endpoint below is written to be directly importable/testable via Postman or wired into the frontend without further backend guesswork.

---

## 1. Scope Lock for This Plan

**In scope (build now):**
- Milestone 1 — Connection Foundation (Application catalog, Connector Registry, Connection CRUD, Test Connection, Health)
- Milestone 2 — Discovery (Capability Assessment, Resource/Operation/Field/Schema Discovery, Metadata normalization + Redis cache)
- Odoo Connector (JSON-2 API) as the first real connector implementation, built strictly behind the `Connector` SPI so a `CustomRestConnector`/`GenericRestConnector` can be added later with zero core-engine changes.

**Explicitly out of scope for this plan** (tracked for Milestones 3–7, see Section 10):
Mapping/Transformation execution, Integration Versioning publish flow, Execution Engine, Redis Streams/Outbox, Retry/DLQ, Scheduling, Webhooks, Generic REST/OpenAPI connector, full security hardening, load testing.

Building Connection + Discovery first (per the locked architecture doc) is what makes every later milestone "extremely simple" — this order must not be changed without sign-off.

---

## 2. Architecture Recap (Locked)

```
CORE ENGINE
   |
   +-------------------+-------------------+
   |                   |                   |
Connection          Discovery           Execution (future)
   |                   |
   +-------------------+
             |
             v
       CONNECTOR SPI  (capability-based interfaces, not one fat interface)
             |
   +---------+---------+
   |                   |
OdooConnector     (future) GenericRestConnector / CustomConnector
```

Domain separation (must never be collapsed into one document/concept):

| Concept | Answers | Collection |
|---|---|---|
| **Application** | "What system is this?" (Odoo, SAP, Custom REST…) | `applications` |
| **Connector** | "What can this implementation do?" (capabilities, auth types, pagination) | `connectors` |
| **Connection** | "How do I connect to this specific instance?" | `connection_configurations` |
| **Integration** (future milestone) | "What business data do I want to exchange?" | `integrations` |

Connector Contract uses **capability-based interfaces**, not one giant interface:
`ReadableConnector`, `WritableConnector`, `DiscoverableConnector`, `WebhookConnector` (future), `IncrementalSyncConnector`. Odoo implements Readable + Writable + Discoverable + IncrementalSync in V1. This is the scalability boundary — the core engine must never contain `if (application == ODOO)` branching; it always goes through the registry.

---

## 3. Proposed Package / Folder Structure

> **Placeholder — confirm against the sibling TPRMS service structure once opened in the workspace, then align package root (`com.tprms.integrationhub` below) and module naming to match exactly.** Do not proceed with scaffolding until this is confirmed (Decision D-1, Section 9).

```
src/main/java/com/tprms/integrationhub/
│
├── IntegrationHubApplication.java
│
├── common/                                  # Shared, reusable across all modules — no business logic here
│   ├── dto/
│   │   ├── ApiResponse.java                 # Generic envelope { success, data, error, traceId }
│   │   ├── PageRequestDto.java
│   │   ├── PageResponseDto.java
│   │   └── ErrorResponseDto.java
│   ├── enums/
│   │   ├── AuthenticationType.java          # API_KEY, BEARER, BASIC, OAUTH2, CUSTOM_HEADER, NONE
│   │   ├── ConnectionStatus.java            # DRAFT, TESTING, ACTIVE, DEGRADED, AUTH_EXPIRED, DISABLED
│   │   ├── ConnectorCapability.java         # READABLE, WRITABLE, DISCOVERABLE, WEBHOOK, BULK, INCREMENTAL_SYNC
	│   ├── PaginationStrategy.java          # PAGE_NUMBER, OFFSET, CURSOR, NEXT_LINK, TOKEN, CUSTOM, NONE
│   │   ├── IncrementalStrategy.java         # FULL, TIMESTAMP, CURSOR, DELTA_TOKEN, VERSION, CUSTOM
│   │   └── HealthStatus.java                # HEALTHY, DEGRADED, UNHEALTHY
│   ├── exception/
│   │   ├── ResourceNotFoundException.java
│   │   ├── ConnectionTestFailedException.java
│   │   ├── UnsupportedCapabilityException.java
│   │   ├── TenantMismatchException.java
│   │   ├── BusinessException.java           # Base checked-unchecked exception; always carries a MessageCode
│   │   └── GlobalExceptionHandler.java      # @RestControllerAdvice — single place for error mapping
│   ├── message/                              # >>> Message Hub — see companion doc Section M for full detail
│   │   ├── MessageCode.java                  # Enum: every error/success code the API can return
│   │   ├── MessageResolver.java              # code -> localized, corporate-toned message string
│   │   ├── messages_en.properties            # Externalized message text (ops/support can edit without redeploy)
│   │   └── ApiMessageResponseBuilder.java     # Builds ApiResponse from a MessageCode (+ dynamic args)
│   ├── security/
│   │   └── TenantContext.java               # ThreadLocal-backed holder resolved from auth token/header
│   ├── mongo/
│   │   ├── BaseRepository.java              # Generic tenant-scoped CRUD base (extends MongoRepository)
│   │   ├── MongoQueryTemplates.java         # Centralized, reusable Criteria/Query builders (Section 6.4)
│   │   └── AuditableDocument.java           # createdAt/updatedAt/createdBy/updatedBy base class
│   ├── query/                                 # >>> Shared pagination/sorting/search kernel — see companion doc Section Q
│   │   ├── PageQueryRequestDto.java           # page, size, sortBy, sortDir, search, filters{}
│   │   ├── SortableFieldRegistry.java         # Per-module allowlist of sortable/searchable fields
│   │   ├── SearchSpecificationBuilder.java    # Builds case-insensitive regex/text Criteria from a search term
│   │   └── PageMetaResponseDto.java           # page, size, totalElements, totalPages, hasNext, hasPrevious
│   └── util/
│       ├── IdGenerator.java                 # Public ID generation (CON-10001, APP-ODOO style)
│       └── MaskingUtil.java                 # Masks secrets/PII before logging (R-015 policy)
│
├── application/                             # "applications" catalog module (What system is this?)
│   ├── controller/ApplicationController.java
│   ├── service/ApplicationService.java
│   ├── service/impl/ApplicationServiceImpl.java
│   ├── repository/ApplicationRepository.java
│   ├── document/ApplicationDocument.java
│   ├── dto/
│   │   ├── request/ (none — read-only catalog in V1)
│   │   └── response/ApplicationResponseDto.java
│   └── mapper/ApplicationMapper.java        # MapStruct interface
│
├── connector/                                # "connectors" registry module (What can this implementation do?)
│   ├── controller/ConnectorController.java
│   ├── service/ConnectorRegistryService.java
│   ├── service/impl/ConnectorRegistryServiceImpl.java
│   ├── repository/ConnectorRepository.java
│   ├── document/ConnectorDocument.java
│   ├── dto/response/ConnectorResponseDto.java
│   ├── mapper/ConnectorMapper.java
│   └── spi/                                  # The Connector Contract — core engine only depends on this package
│       ├── Connector.java                    # Marker/base — connectorId(), applicationId()
│       ├── ReadableConnector.java             # read(), search()
│       ├── WritableConnector.java             # create(), update(), delete()
│       ├── DiscoverableConnector.java         # discoverResources/Operations/Fields/Schema
│       ├── IncrementalSyncConnector.java      # supportsIncremental(), incrementalStrategies()
│       ├── ConnectionTestable.java            # testConnection()
│       └── model/                             # Connector-agnostic normalized models (Section 5)
│           ├── NormalizedResource.java
│           ├── NormalizedOperation.java
│           ├── NormalizedField.java
│           ├── CapabilityAssessment.java
│           ├── ConnectionTestResult.java
│           └── ExternalRecord.java            # Generic key/value record returned by any connector
│
├── connection/                                # "connection_configurations" module (How do I connect?)
│   ├── controller/ConnectionController.java
│   ├── service/ConnectionService.java
│   ├── service/impl/ConnectionServiceImpl.java
│   ├── repository/ConnectionRepository.java
│   ├── repository/custom/ConnectionCustomRepository.java
│   ├── repository/custom/impl/ConnectionCustomRepositoryImpl.java
│   ├── document/ConnectionConfigurationDocument.java
│   ├── dto/
│   │   ├── request/CreateConnectionRequestDto.java
│   │   ├── request/UpdateConnectionRequestDto.java
│   │   ├── request/TestConnectionRequestDto.java
│   │   ├── response/ConnectionResponseDto.java
│   │   ├── response/ConnectionSummaryResponseDto.java     # lightweight, for list views
│   │   ├── response/ConnectionTestResponseDto.java
│   │   └── response/ConnectionHealthResponseDto.java
│   ├── mapper/ConnectionMapper.java
│   └── credential/
│       ├── CredentialReferenceService.java   # Talks to Secret Manager; never returns raw secret
│       └── SecretManagerClient.java          # Thin client — implementation swappable
│
├── discovery/                                 # Discovery Engine module (capability + resource/op/field/schema)
│   ├── controller/DiscoveryController.java
│   ├── service/DiscoveryService.java
│   ├── service/impl/DiscoveryServiceImpl.java
│   ├── service/CapabilityAssessmentService.java
│   ├── service/impl/CapabilityAssessmentServiceImpl.java
│   ├── cache/DiscoveryCacheService.java       # Redis-backed, TTL + versioned keys (Section 6.5)
│   ├── dto/
│   │   ├── response/CapabilityAssessmentResponseDto.java
│   │   ├── response/ResourceResponseDto.java
│   │   ├── response/OperationResponseDto.java
│   │   └── response/FieldResponseDto.java
│   └── mapper/DiscoveryMapper.java            # Normalized SPI model -> API DTO
│
├── odoo/                                      # Odoo Connector implementation — connector-specific knowledge only
│   ├── OdooConnector.java                     # implements ReadableConnector, WritableConnector,
│   │                                          #            DiscoverableConnector, IncrementalSyncConnector,
│   │                                          #            ConnectionTestable
│   ├── client/
│   │   ├── OdooJson2Client.java               # Thin HTTP client wrapping /json/2/<model>/<method>
│   │   └── OdooClientConfig.java              # Timeouts, connection pool, WebClient/RestClient bean
│   ├── dto/                                    # Odoo wire-format DTOs — never leak outside this package
│   │   ├── request/OdooSearchReadRequest.java
│   │   ├── request/OdooFieldsGetRequest.java
│   │   ├── request/OdooCreateRequest.java
│   │   ├── request/OdooWriteRequest.java
│   │   ├── response/OdooSearchReadResponse.java
│   │   ├── response/OdooFieldsGetResponse.java
│   │   ├── response/OdooErrorResponse.java
│   │   └── response/OdooVersionResponse.java
│   ├── mapper/
│   │   ├── OdooFieldMetadataMapper.java       # Odoo fields_get output -> NormalizedField
│   │   └── OdooModelMapper.java               # ir.model rows -> NormalizedResource
│   └── exception/OdooApiException.java        # Wraps Odoo's HTTP 4xx/5xx JSON error object
│
├── audit/                                      # audit_logs module (cross-cutting, but isolated persistence)
│   ├── service/AuditService.java
│   ├── service/impl/AuditServiceImpl.java
│   ├── repository/AuditLogRepository.java
│   ├── document/AuditLogDocument.java
│   └── dto/AuditLogEntry.java
│
└── config/
    ├── MongoConfig.java                        # Index creation on startup (Section 6.2), converters
    ├── RedisConfig.java
    ├── WebClientConfig.java
    └── OpenApiConfig.java                      # Swagger/OpenAPI so Postman collection can be generated
```

**Naming conventions (applies to every module above):**
- Documents: `<Noun>Document` — mirrors Mongo collection singular concept (`ConnectionConfigurationDocument` → `connection_configurations`).
- Request DTOs: `<Action><Noun>RequestDto`. Response DTOs: `<Noun>ResponseDto` (or `<Noun>SummaryResponseDto` for list-view projections).
- Mappers: `<Noun>Mapper`, MapStruct interfaces — **no manual field-by-field mapping code in services.**
- Services: interface `<Noun>Service` + `impl/<Noun>ServiceImpl` — enables mocking in tests and keeps controllers thin.
- Repositories: interface `<Noun>Repository extends BaseRepository<...>`; custom/complex queries go in `<Noun>CustomRepository` + `impl/<Noun>CustomRepositoryImpl` (Spring Data custom-repository pattern) — **this is where all `Criteria`/aggregation queries live, never in services.**

---

## 4. API Endpoints (Postman/Frontend-Ready)

Base path: `/api/v1/integration-hub`. All endpoints require a tenant-resolving auth header (e.g. `Authorization: Bearer <token>` → resolved into `TenantContext`); every query is tenant-scoped server-side regardless of what the client sends.

Every response below (success or error) is wrapped in the `ApiResponse` envelope and driven by a `MessageCode` — see **Section M**. Every `GET` list endpoint (`/applications`, `/connectors`, `/connections`) accepts the shared `page/size/sortBy/sortDir/search` query params and returns the shared `PagedResponseDto` shape — see **Section Q**. Endpoint tables below show the business payload only; wrap accordingly.

### 4.1 Applications (catalog, read-only in V1)
| Method | Path | Purpose |
|---|---|---|
| GET | `/applications` | List available applications (Odoo, Generic REST, Custom REST) |
| GET | `/applications/{applicationId}` | Get one application's detail |

### 4.2 Connectors (registry, read-only in V1)
| Method | Path | Purpose |
|---|---|---|
| GET | `/connectors` | List connector implementations + declared capabilities |
| GET | `/connectors/{connectorId}` | Get one connector's capability/auth/pagination matrix |

### 4.3 Connections (Milestone 1 — the core module)
| Method | Path | Purpose |
|---|---|---|
| POST | `/connections` | Create connection (status starts `DRAFT`) |
| GET | `/connections` | List connections for tenant (paginated, filter by `applicationId`, `status`) |
| GET | `/connections/{connectionId}` | Get one connection (credential never included) |
| PUT | `/connections/{connectionId}` | Update non-secret connection fields |
| DELETE | `/connections/{connectionId}` | Disable connection (soft — sets `status=DISABLED`, never hard-deletes if referenced by integrations) |
| POST | `/connections/{connectionId}/test` | Explicit Test Connection — see Section 5.3 flow |
| GET | `/connections/{connectionId}/health` | Rolling health (from execution outcomes, not live polling) |
| POST | `/connections/{connectionId}/rotate-credential` | Rotate `credentialReference` without recreating the connection |

**Example — Create Connection (`POST /connections`):**
```json
{
  "applicationId": "APP-ODOO",
  "name": "Odoo Production",
  "protocol": "JSON-2",
  "authenticationType": "API_KEY",
  "endpoint": { "baseUrl": "https://acme.odoo.com", "database": "acme_prod" },
  "credential": { "apiKey": "***provided once, forwarded straight to Secret Manager, never persisted in request logs***" },
  "rateLimit": { "maxConcurrency": 4, "requestsPerSecond": 5, "burstLimit": 10 }
}
```
Response (`201 Created`): `ConnectionResponseDto` — includes `connectionId`, `status: "DRAFT"`, and **never** the credential.

**Example — Test Connection (`POST /connections/{connectionId}/test`):**
```json
{ "status": "SUCCESS", "latencyMs": 240, "testedAt": "2026-08-15T10:00:00Z", "odooVersion": "19.0", "message": "Connection successful" }
```
On success, connection transitions `DRAFT/TESTING → ACTIVE` and a `CapabilityAssessment` (Section 4.4) is computed and stored on the connection document.

### 4.4 Discovery (Milestone 2)
| Method | Path | Purpose |
|---|---|---|
| GET | `/connections/{connectionId}/capabilities` | Return the stored capability assessment (recompute only if stale/forced) |
| POST | `/connections/{connectionId}/capabilities/refresh` | Force re-assessment |
| GET | `/connections/{connectionId}/resources` | Discover resources (Odoo: `ir.model` search_read) — Redis-cached |
| GET | `/connections/{connectionId}/resources/{resourceKey}/operations` | Discover operations for one resource |
| GET | `/connections/{connectionId}/resources/{resourceKey}/fields` | Discover fields (Odoo: `fields_get`) — Redis-cached |
| GET | `/connections/{connectionId}/resources/{resourceKey}/sample` | Fetch a small sample record set (bounded `limit`, default 5) for preview/mapping UI |
| POST | `/connections/{connectionId}/resources/manual` | Manually register a resource when auto-discovery is unavailable (Case C, generic REST) |

**Example — Discover Fields Response (`GET /resources/res.partner/fields`):**
```json
{
  "resourceKey": "res.partner",
  "fields": [
    { "technicalName": "name", "displayName": "Name", "dataType": "STRING", "required": true, "relation": null },
    { "technicalName": "email", "displayName": "Email", "dataType": "STRING", "required": false, "relation": null },
    { "technicalName": "country_id", "displayName": "Country", "dataType": "RELATION_MANY2ONE", "required": false, "relation": "res.country" }
  ]
}
```

All discovery responses are served through `DiscoveryCacheService` (Redis) with a TTL and cache key `integration:metadata:{connectionId}:{resourceKey}` — see Section 6.5. They are **never persisted to MongoDB** (per the locked "durable config vs rebuildable metadata" rule).

---

## 5. Core Flows (Sequence-Level Detail)

### 5.1 Connector SPI Contract
```java
public interface Connector {
    String connectorId();
    String applicationId();
}

public interface ConnectionTestable {
    ConnectionTestResult testConnection(ConnectionContext context);
}

public interface DiscoverableConnector {
    List<NormalizedResource> discoverResources(ConnectionContext context);
    List<NormalizedOperation> discoverOperations(ConnectionContext context, String resourceKey);
    List<NormalizedField> discoverFields(ConnectionContext context, String resourceKey);
}

public interface ReadableConnector {
    PagedResult<ExternalRecord> read(ConnectionContext context, ReadQuery query);
}

public interface WritableConnector {
    ExternalRecord create(ConnectionContext context, String resourceKey, Map<String, Object> values);
    void update(ConnectionContext context, String resourceKey, String externalId, Map<String, Object> values);
}

public interface IncrementalSyncConnector {
    Set<IncrementalStrategy> supportedIncrementalStrategies();
}
```
`ConnectionContext` carries the resolved, decrypted-at-use credential (fetched just-in-time from `CredentialReferenceService`, never cached in plaintext), base URL, database, and rate-limit settings — it is built once per request by `ConnectionService` and passed down; connectors never talk to Mongo or the Secret Manager directly.

### 5.2 Connector Registry Resolution
```
ConnectionService needs a Connector for connectionId
    -> load ConnectionConfigurationDocument (has connectorId)
    -> ConnectorRegistryService.resolve(connectorId) -> Connector bean
    -> Spring bean map, keyed by connectorId, populated at startup
       (each Connector impl is a @Component annotated with its connectorId,
        collected into Map<String, Connector> via constructor injection —
        NOT an if/else chain)
```
This is the concrete implementation of the "no `if (application == ODOO)`" rule.

### 5.3 Test Connection Flow (strictly separated from discovery — per locked plan)
```
POST /connections/{id}/test
  -> ConnectionService.testConnection(connectionId)
  -> resolve Connector via registry
  -> resolve credential reference (JIT, not persisted in memory beyond the call)
  -> connector.testConnection(context)   // Odoo: GET /web/version + one authenticated search_read (limit 1)
  -> on SUCCESS:
       - update connection.status -> ACTIVE
       - update lastTestedAt / lastTestStatus / lastTestErrorCode / lastSuccessfulAt
       - trigger CapabilityAssessmentService.assess(connectionId) (Section 5.4)
       - AuditService.record("CONNECTION_TESTED", ...)
  -> on FAILURE:
       - classify error (AUTH_EXPIRED vs generic FAILED) per Section 6.3
       - update connection.status accordingly, persist lastTestErrorCode
  -> return ConnectionTestResponseDto
```
Must **not** synchronously pull full resource/field lists — that is Discovery's job (Section 5.5), executed only after activation.

### 5.4 Capability Assessment
```
CapabilityAssessmentService.assess(connectionId)
  -> connector.testConnection() already confirmed reachability
  -> for each capability declared by the Connector's registry entry (connectors.capabilities):
       - determine supported (declared by connector), available (works for this instance/permissions),
         configured (does the current connection config satisfy prerequisites), reason (if not configured)
  -> persist CapabilityAssessment array onto connection_configurations.capabilities
  -> cache a copy in Redis for fast repeated reads (short TTL, since Mongo is already the source of truth here)
```
Output shape matches the locked format exactly — never collapse to `{ "read": true }`:
```json
{ "name": "WEBHOOK", "supported": true, "available": true, "configured": false, "reason": "Webhook configuration required" }
```

### 5.5 Discovery Flow (Resource → Operation → Field, cached)
```
GET /connections/{id}/resources/{resourceKey}/fields
  -> DiscoveryCacheService.get(key) -- Redis hit? return immediately (no external call)
  -> cache miss:
       -> resolve Connector -> discoverFields(context, resourceKey)
       -> Odoo path: OdooJson2Client.post("/json/2/{model}/fields_get", attributes=[...])
                     -> OdooFieldMetadataMapper maps Odoo field map -> List<NormalizedField>
       -> DiscoveryMapper maps NormalizedField -> FieldResponseDto
       -> DiscoveryCacheService.put(key, value, ttl)
  -> return FieldResponseDto list
```
Generic REST (future) follows the 3-case rule from the catalog: OpenAPI present → parse; metadata endpoint present → use it; neither → return `"Automatic discovery unavailable"` and route the frontend to the manual "Add Resource" endpoint (`POST /resources/manual`) — this contract already exists in Section 4.4 so the future connector needs no new endpoint.

---

## 6. Data, DTO & Mapper Details

### 6.1 MongoDB Collections in Scope for This Plan
Only the following (from the 11-collection model) are actively written to by this plan; the rest (`integrations`, `integration_versions`, `integration_executions`, `execution_failures`, `entity_identity_map`, `webhook_endpoints`) are **read-only placeholders / future milestones** and must not be created by this build unless explicitly needed for a foreign-key stub.

- `tenants` (read-only lookup — assume it already exists in the sibling service)
- `applications`
- `connectors`
- `connection_configurations`
- `audit_logs`

Field-level schema for each: **exactly as specified in `MongoDB_Data_Model_TPRMS_Integration_Hub.docx` Section 3.2–3.4 and 3.11** — do not deviate. Reproduced summary:

**`connection_configurations`** (the core document of this plan):
`connectionId, tenantId, applicationId, connectorId, name, protocol, authenticationType, endpoint{baseUrl,database,region}, credentialReference, capabilities[], rateLimit{maxConcurrency,requestsPerSecond,burstLimit}, status, health{status,consecutiveFailures,lastHealthyAt}, lastTestedAt, lastTestStatus, lastTestErrorCode, lastSuccessfulAt, createdBy, updatedBy, createdAt, updatedAt`

Indexes (create via `MongoConfig` on startup, matching the locked doc):
```
Unique: { tenantId: 1, connectionId: 1 }
Index:  { tenantId: 1, applicationId: 1 }
Index:  { tenantId: 1, status: 1 }
```

### 6.2 DTO ↔ Document ↔ Connector-Model Mapping Chain

There are **three distinct model layers** — never let one leak into another:

```
Wire DTO (API request/response)  <-- Mapper -->  MongoDB Document  <-- (not directly mapped) -->  Connector SPI Model
        ^                                                                          |
        |                                                                          v
        +------------------------  DiscoveryMapper  <-----------------------------+
```

| Layer | Example class | Lives in |
|---|---|---|
| Wire DTO | `ConnectionResponseDto`, `FieldResponseDto` | `*/dto/request|response` |
| Document | `ConnectionConfigurationDocument` | `*/document` |
| SPI Model | `NormalizedField`, `CapabilityAssessment` | `connector/spi/model` |
| Odoo Wire DTO | `OdooFieldsGetResponse` | `odoo/dto/response` |

**Rule:** `OdooFieldsGetResponse` → `NormalizedField` (via `OdooFieldMetadataMapper`, connector-specific) → `FieldResponseDto` (via `DiscoveryMapper`, connector-agnostic). The Odoo wire format never reaches the controller layer. This is what lets a future `GenericRestConnector` reuse the exact same `DiscoveryMapper` and `FieldResponseDto`.

### 6.3 Mapper Implementation Standard
Use **MapStruct** (compile-time, zero reflection overhead — performance requirement) for all Document ↔ DTO and SPI-Model ↔ DTO mappings. Example:

```java
@Mapper(componentModel = "spring")
public interface ConnectionMapper {

    // Document -> Response DTO. credentialReference is intentionally NOT mapped (see @Mapping ignore)
    // — this enforces "never return the secret" at the mapping layer, not just by convention.
    @Mapping(target = "credentialReference", ignore = true)
    ConnectionResponseDto toResponseDto(ConnectionConfigurationDocument document);

    List<ConnectionSummaryResponseDto> toSummaryDtoList(List<ConnectionConfigurationDocument> documents);

    // Request DTO -> new Document. connectionId/status/timestamps are set by the service layer,
    // not the mapper, because they depend on business rules (ID generation, initial status).
    @Mapping(target = "connectionId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "credentialReference", ignore = true)
    ConnectionConfigurationDocument toNewDocument(CreateConnectionRequestDto request);
}
```

**Error classification mapper** (`OdooApiException` → connection status), centralized so both Test Connection and Execution (future) reuse the same rule instead of duplicating retry/auth logic:
```java
// Why: HTTP 401 means the API key itself is invalid/expired — surfacing this as AUTH_EXPIRED
// (not a generic FAILED) lets the frontend show "reconnect" instead of "retry", per the locked UX plan.
public ConnectionStatus classify(int httpStatus) {
    if (httpStatus == 401) return ConnectionStatus.AUTH_EXPIRED;
    if (httpStatus == 403) return ConnectionStatus.DEGRADED;
    return ConnectionStatus.DEGRADED;
}
```

### 6.4 Reusable Mongo Query Templates (`common/mongo/MongoQueryTemplates.java`)

**Purpose:** every tenant-scoped, filterable, paginated query used across `ConnectionCustomRepositoryImpl`, `ApplicationRepository`, and future modules is built from this one place — services and controllers never construct `Criteria`/`Query` objects themselves.

```java
@Component
public final class MongoQueryTemplates {

    // Why: tenant isolation is enforced at the query layer itself, not trusted to be added
    // by every caller — this is the single choke point R-... (tenant isolation) runs through.
    public Criteria tenantScoped(String tenantId) {
        return Criteria.where("tenantId").is(tenantId);
    }

    public Criteria byPublicId(String fieldName, String value) {
        return Criteria.where(fieldName).is(value);
    }

    public Criteria optionalEquals(String fieldName, Object value) {
        return value == null ? new Criteria() : Criteria.where(fieldName).is(value);
    }

    // Reusable pagination + sort builder — keeps every list endpoint consistent
    // and prevents unbounded "find all" queries (performance requirement).
    public Pageable toPageable(PageRequestDto request, String defaultSortField) {
        int size = Math.min(request.getSize() == null ? 20 : request.getSize(), 100); // hard cap
        Sort sort = Sort.by(Sort.Direction.DESC,
                request.getSortField() != null ? request.getSortField() : defaultSortField);
        return PageRequest.of(request.getPage() == null ? 0 : request.getPage(), size, sort);
    }

    // Field-projection helper — only fetch what the response DTO actually needs
    // (e.g. list views never need `capabilities`/`rateLimit`), reducing document transfer size.
    public Query withProjection(Query query, String... includedFields) {
        for (String field : includedFields) query.fields().include(field);
        return query;
    }
}
```

`ConnectionCustomRepositoryImpl` example usage (the **only** place allowed to combine these into a full query):
```java
@Repository
public class ConnectionCustomRepositoryImpl implements ConnectionCustomRepository {

    private final MongoTemplate mongoTemplate;
    private final MongoQueryTemplates templates;

    @Override
    public Page<ConnectionConfigurationDocument> search(String tenantId, ConnectionSearchCriteria criteria, PageRequestDto page) {
        // Why: combine tenant scope (mandatory) with optional applicationId/status filters
        // without ever letting the caller bypass the tenant clause.
        Criteria combined = new Criteria().andOperator(
                templates.tenantScoped(tenantId),
                templates.optionalEquals("applicationId", criteria.getApplicationId()),
                templates.optionalEquals("status", criteria.getStatus())
        );
        Query query = Query.query(combined).with(templates.toPageable(page, "createdAt"));
        List<ConnectionConfigurationDocument> results = mongoTemplate.find(query, ConnectionConfigurationDocument.class);
        long total = mongoTemplate.count(Query.query(combined), ConnectionConfigurationDocument.class);
        return new PageImpl<>(results, query.getPageable(), total);
    }
}
```

### 6.5 Redis Cache Key Templates (`discovery/cache/DiscoveryCacheService.java`)

```
integration:capability:{tenantId}:{connectionId}                      TTL: 6h
integration:metadata:resources:{tenantId}:{connectionId}              TTL: 24h
integration:metadata:fields:{tenantId}:{connectionId}:{resourceKey}   TTL: 24h
```
- Versioned by connection's `updatedAt`/Odoo version where relevant, so a credential rotation or Odoo upgrade naturally invalidates stale cache without a manual flush step.
- `DiscoveryCacheService` exposes only `get(key, type)`, `put(key, value, ttl)`, `evict(pattern)` — callers never touch `RedisTemplate` directly, matching the "no logic scattered in business code" rule for Redis too, not just Mongo.

---

## 7. Odoo Connector — Implementation Notes (from the Catalog)

Mapped directly from `Odoo_JSON2_API_Integration_Catalog.xlsx` (authoritative endpoint reference):

| TPRMS Capability | Odoo JSON-2 Endpoint | Notes (from catalog rules) |
|---|---|---|
| Version/connection check | `GET /web/version` | Called first in `testConnection()`, reject unsupported versions per policy |
| Auth verification | `POST /json/2/res.users/search_read` (limit 1) | Confirms API key + access rights, not just reachability |
| Resource discovery | `POST /json/2/ir.model/search_read` | Discover installed models — **never hardcode the model universe (R-002)** |
| Field discovery | `POST /json/2/<model>/fields_get` | Cache aggressively; use technical field names, not labels (R-006) |
| Read | `POST /json/2/<model>/search_read` | Always pass explicit `fields[]`; use `offset/limit/order` (R-005) |
| Read by ID | `POST /json/2/<model>/read` | Used for delta/exact-ID reads |
| Count | `POST /json/2/<model>/search_count` | Use sparingly, not per grid request |
| Create | `POST /json/2/<model>/create` | Validate required fields + selection values before calling |
| Update | `POST /json/2/<model>/write` | Never blind-overwrite; send only changed fields |
| Business actions (future) | `POST /json/2/<model>/<action_method>` | e.g. `action_confirm` — model/version dependent, out of scope for this plan |

**Hard rules baked into `OdooConnector` / `OdooJson2Client`:**
- Auth: `Authorization: Bearer <API_KEY>` — key comes from `ConnectionContext`, resolved just-in-time; **never logged** (`MaskingUtil` wraps all request/response logging in this client).
- Relational fields (Many2one/One2many/Many2many) are **not flattened** — `OdooFieldMetadataMapper` marks them with a `relation` target model so the mapping UI (future milestone) can resolve them explicitly, per R-007.
- Dates: Odoo datetime values are normalized to UTC ISO-8601 in `OdooFieldMetadataMapper`/read-time conversion, per R-008.
- Incremental sync uses `write_date`/`create_date` filters (R-009) — `IncrementalSyncConnector.supportedIncrementalStrategies()` returns `{TIMESTAMP}` for Odoo in V1.
- Errors: Odoo 4xx/5xx JSON error body is parsed into `OdooErrorResponse` → wrapped as `OdooApiException` → classified by `ConnectionMapper`'s error classifier (Section 6.3) — never expose Odoo's raw traceback to the frontend, only a correlation ID + safe message (R-013/R-042 catalog rule).
- Legacy `/jsonrpc` and `/xmlrpc/2/*` are **not implemented** — JSON-2 only, per catalog note R-001/Sources sheet.

---

## 8. Execution Plan — Build Order (Sprint-Sliceable)

Do not reorder without sign-off; each step is a dependency for the next.

**Step 1 — Scaffolding & Shared Kernel**
1. Confirm sibling-service package/folder conventions (Decision D-1) and align `common/`.
2. Implement `common/` (DTO envelope, exceptions, `TenantContext`, `BaseRepository`, `MongoQueryTemplates`, `AuditableDocument`).
3. Implement `MongoConfig` (index creation), `RedisConfig`, `OpenApiConfig`.

**Step 2 — Application & Connector Registry (read-only catalogs)**
4. `applications` module: document, repository, mapper, `GET /applications[/{id}]`.
5. `connectors` module: document, repository, mapper, `GET /connectors[/{id}]`.
6. Seed data (script or migration) for `APP-ODOO` + `CONN-ODOO-V1` matching the locked JSON example.

**Step 3 — Connector SPI**
7. Define `connector/spi` interfaces + normalized models (Section 5.1) — no implementation yet.
8. `ConnectorRegistryService` bean-map resolution mechanism (Section 5.2).

**Step 4 — Connection Foundation (Milestone 1)**
9. `connection` module: document, repository, custom repository, mapper, DTOs.
10. `CredentialReferenceService` + `SecretManagerClient` (interface first; confirm concrete Secret Manager choice — Decision D-2).
11. `ConnectionService`: create / update / list / get / disable.
12. `POST /connections/{id}/test` wired to `ConnectorRegistryService` + a **stub/mock connector** first (to prove the flow end-to-end before Odoo exists).
13. `GET /connections/{id}/health`.
14. **Acceptance gate:** connection CRUD + test works against the stub connector, fully tenant-isolated, verified in Postman.

**Step 5 — Odoo Connector**
15. `OdooJson2Client` (HTTP client, auth header, timeouts, error parsing).
16. `OdooConnector.testConnection()` — `/web/version` + one `search_read`.
17. Wire real Odoo instance into Step 4's Test Connection flow — replace the stub.
18. **Acceptance gate:** Odoo connection can be created once, tested, and reused (matches Milestone 1 acceptance criterion in the locked plan).

**Step 6 — Discovery (Milestone 2)**
19. `CapabilityAssessmentService` + `discovery/cache` (Redis).
20. `OdooConnector.discoverResources()` (`ir.model`), `discoverFields()` (`fields_get`), mappers.
21. `discovery` module controller/service wiring all endpoints in Section 4.4.
22. **Acceptance gate:** Odoo can show Contacts/Sales Orders/Products and their fields via API, without any hardcoded model list (matches Milestone 2 acceptance criterion).

**Step 7 — Hardening for This Slice**
23. Audit logging on connection create/update/test/disable (`audit_logs`).
24. Input validation (`@Valid` + Bean Validation annotations on every request DTO).
25. Rate-limit/timeout tuning on `OdooJson2Client` per `connection.rateLimit`.
26. Postman collection export from OpenAPI spec, committed alongside the code.

Everything past this point (Mapping Engine, Integration Versions, Execution Engine, Redis Streams, Webhooks, Generic REST connector) is **Milestone 3+** and intentionally deferred — see Section 10.

---

## 9. Decisions Requiring Sign-Off Before/During Build

| ID | Decision | Why it matters | Status |
|---|---|---|---|
| D-1 | Exact package root, module boundaries, and file/package naming to mirror the sibling TPRMS service | Directly affects Section 3's folder structure | **Pending — open sibling service in workspace** |
| D-2 | Concrete Secret Manager product/SDK (e.g. AWS Secrets Manager, HashiCorp Vault, Azure Key Vault) | `SecretManagerClient` implementation depends on it | **Pending** |
| D-3 | Auth/tenant resolution mechanism already in use by the sibling service (JWT claims? header? gateway-injected?) | `TenantContext` population logic | **Pending** |
| D-4 | MapStruct vs. existing mapping convention already used in the sibling service | Section 6.3 assumes MapStruct; must match existing convention if different | **Pending** |
| D-5 | Minimum supported Odoo version enforcement rule at Test Connection time | Catalog says "reject unsupported versions accordingly" but doesn't fix the cutoff | **Pending** |
| D-6 | Rate limit defaults per connection (maxConcurrency/requestsPerSecond/burstLimit) | Affects `OdooJson2Client` throttling defaults | **Pending — propose 4 / 5 / 10 per locked JSON example, confirm** |

No code for these areas should assume an answer silently — stub with a clearly marked `// TODO(D-x): confirm before production` and the safest default.

---

## M. Message Hub — Centralized Error & Success Messages

**Goal:** every controller/service returns messages through one hub, never a hand-typed string. This guarantees consistent, corporate, user-friendly wording, makes copy changes a one-file edit (no redeploy-through-code-review for wording tweaks), and gives support/QA a stable code to search logs by.

### M.1 Design Principles

- **One enum, one source of truth.** `MessageCode` is the only place a message identifier is defined. Nothing else invents a string like `"Connection not found"` inline.
- **Code + Message are both returned**, never message-only — the frontend can branch on `code` (e.g. show a "Reconnect" button for `CONN_AUTH_EXPIRED`) while the `message` is what the user reads.
- **User-facing text is corporate, calm, and actionable** — states what happened and, where relevant, what to do next. Never exposes stack traces, Mongo errors, or raw upstream (Odoo) error bodies.
- **Technical detail is never lost** — it goes to `traceId`-correlated logs, not into the HTTP response body.
- **Externalized strings** (`messages_en.properties`), not hardcoded in Java — enables future localization and non-engineer copy edits without touching compiled code.
- **Every `BusinessException` carries a `MessageCode`**, not a raw string — the exception handler is the only place that turns a code into an HTTP status + response body.

### M.2 Response Envelope (used for both success and error)

```java
public class ApiResponse<T> {
    private boolean success;
    private String code;          // e.g. "CONN_TEST_SUCCESS" or "CONN_NOT_FOUND"
    private String message;       // corporate, user-friendly text resolved from the code
    private T data;                // present on success, null on error
    private List<FieldErrorDto> fieldErrors; // present only for VALIDATION_FAILED, else empty
    private String traceId;       // correlation ID for support/log lookup — always present
    private Instant timestamp;
}
```

Every controller returns `ApiResponse<T>` — never a bare DTO and never a bare `ResponseEntity<String>` for errors. This is enforced by `GlobalExceptionHandler` for the error path and by `ApiMessageResponseBuilder` for the success path, so individual controllers cannot drift from the format.

### M.3 `MessageCode` — Structure & Naming

Format: `<MODULE>_<OUTCOME>`, all-caps, underscore-separated. Grouped by module so the enum stays scannable as it grows; **do not create a new code for something an existing one already covers** — reuse and pass dynamic args instead (e.g. one `RESOURCE_NOT_FOUND` with a `{resource}` placeholder, not `CONNECTION_NOT_FOUND` + `APPLICATION_NOT_FOUND` + `CONNECTOR_NOT_FOUND` as three separate codes, unless the corporate wording genuinely needs to differ).

```java
public enum MessageCode {

    // ---- Generic / cross-cutting ----
    SUCCESS("Request completed successfully."),
    VALIDATION_FAILED("Some of the information provided is invalid. Please review the highlighted fields."),
    RESOURCE_NOT_FOUND("The requested {resource} could not be found."),
    UNAUTHORIZED("Your session could not be verified. Please sign in again."),
    ACCESS_DENIED("You do not have permission to perform this action."),
    TENANT_MISMATCH("This resource does not belong to your organization."),
    RATE_LIMITED("Too many requests. Please wait a moment and try again."),
    INTERNAL_ERROR("Something went wrong on our end. Our team has been notified. Reference: {traceId}"),

    // ---- Connection module ----
    CONNECTION_CREATED("Connection \"{name}\" was created successfully."),
    CONNECTION_UPDATED("Connection \"{name}\" was updated successfully."),
    CONNECTION_DISABLED("Connection \"{name}\" has been disabled."),
    CONNECTION_NOT_FOUND("We couldn't find that connection. It may have been removed."),
    CONNECTION_DUPLICATE_NAME("A connection named \"{name}\" already exists. Please choose a different name."),
    CONNECTION_TEST_SUCCESS("Connection successful. \"{name}\" is ready to use."),
    CONNECTION_TEST_FAILED_AUTH("We couldn't authenticate with \"{name}\". Please check the credentials and try again."),
    CONNECTION_TEST_FAILED_UNREACHABLE("We couldn't reach \"{name}\" at the provided address. Please verify the URL and network access."),
    CONNECTION_TEST_FAILED_UNKNOWN("The connection test did not succeed. Please review the configuration and try again."),
    CONNECTION_AUTH_EXPIRED("The credentials for \"{name}\" have expired. Please reconnect to continue syncing."),
    CONNECTION_VERSION_UNSUPPORTED("\"{name}\" is running an unsupported version ({version}). Please upgrade or contact support."),

    // ---- Discovery module ----
    DISCOVERY_UNAVAILABLE("Automatic discovery isn't available for this connection. You can add the resource manually."),
    DISCOVERY_CAPABILITY_NOT_CONFIGURED("This feature requires additional configuration before it can be used."),
    DISCOVERY_RESOURCE_ACCESS_DENIED("Access to \"{resource}\" was denied by the source system. Please check permissions."),

    // ---- Odoo connector ----
    ODOO_UPSTREAM_ERROR("The Odoo system returned an error while processing this request."),
    ODOO_PERMISSION_DENIED("Odoo denied access to this data. Please check the API user's permissions in Odoo."),
    ODOO_MODEL_NOT_FOUND("The Odoo model \"{model}\" was not found. It may not be installed on this instance.");

    private final String defaultTemplate;
    MessageCode(String defaultTemplate) { this.defaultTemplate = defaultTemplate; }
    public String template() { return defaultTemplate; }
}
```

Comment intent: the `{placeholder}` tokens are resolved by `MessageResolver` against a `Map<String,Object>` of args supplied at the throw/return site — this keeps wording centralized while still letting a message reference the specific connection name, resource, or trace ID involved.

### M.4 `MessageResolver` — Resolution Order

```java
@Component
public class MessageResolver {

    // Why: properties file is checked first so support/localization teams can override wording
    // without a code change; the enum's defaultTemplate is the guaranteed fallback so a missing
    // properties key never breaks the API response.
    public String resolve(MessageCode code, Map<String, Object> args) {
        String template = messageSource.getMessage(code.name(), null, code.template(), Locale.getDefault());
        return interpolate(template, args); // replaces {resource}, {name}, {traceId}, etc.
    }
}
```

`messages_en.properties` mirrors every enum entry as `CODE=Corporate wording here` — this is the file a non-engineer edits for copy changes; Java code never needs to change for wording-only updates.

### M.5 How It's Used End-to-End

**Throwing an error (service layer — never builds the HTTP response itself):**
```java
// Why: service layer only knows "this failed and why" (the code + args); it has no opinion
// on HTTP status or response shape — that's GlobalExceptionHandler's job, kept in one place.
if (connection == null) {
    throw new BusinessException(MessageCode.CONNECTION_NOT_FOUND, Map.of());
}
if (odooResponse.isAuthError()) {
    throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", connection.getName()));
}
```

**`GlobalExceptionHandler` — the only place that maps code → HTTP status:**
```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<Void>> handle(BusinessException ex) {
    HttpStatus status = HttpStatusMapper.forCode(ex.getCode()); // e.g. NOT_FOUND, 401, 409, 502
    String message = messageResolver.resolve(ex.getCode(), ex.getArgs());
    String traceId = TraceIdProvider.current();
    log.warn("code={} traceId={} detail={}", ex.getCode(), traceId, ex.getTechnicalDetail()); // full detail: logs only
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getCode(), message, traceId));
}
```

**Returning success (controller/service, via the builder — never `new ApiResponse<>(true, ...)` inline):**
```java
return apiMessageResponseBuilder.success(MessageCode.CONNECTION_TEST_SUCCESS,
        Map.of("name", connection.getName()), connectionTestResponseDto);
```

**Example HTTP responses actually returned to the frontend:**
```json
{
  "success": true,
  "code": "CONNECTION_TEST_SUCCESS",
  "message": "Connection successful. \"Odoo Production\" is ready to use.",
  "data": { "latencyMs": 240, "odooVersion": "19.0" },
  "traceId": "a1b2c3d4",
  "timestamp": "2026-08-15T10:00:00Z"
}
```
```json
{
  "success": false,
  "code": "CONNECTION_TEST_FAILED_AUTH",
  "message": "We couldn't authenticate with \"Odoo Production\". Please check the credentials and try again.",
  "data": null,
  "fieldErrors": [],
  "traceId": "e5f6a7b8",
  "timestamp": "2026-08-15T10:00:05Z"
}
```

### M.6 Validation Errors — `fieldErrors` Detail

`VALIDATION_FAILED` is the one code that always populates `fieldErrors`, keeping per-field detail out of the top-level `message` (which stays generic and corporate) while still giving the frontend exactly what it needs to highlight fields:
```json
{
  "success": false,
  "code": "VALIDATION_FAILED",
  "message": "Some of the information provided is invalid. Please review the highlighted fields.",
  "fieldErrors": [
    { "field": "endpoint.baseUrl", "message": "Base URL must be a valid HTTPS address." },
    { "field": "name", "message": "Connection name is required." }
  ],
  "traceId": "c9d0e1f2",
  "timestamp": "2026-08-15T10:00:10Z"
}
```
Bean Validation (`@Valid` + `@NotBlank`/`@Pattern`/etc. on request DTOs) produces `MethodArgumentNotValidException`; a dedicated `GlobalExceptionHandler` branch converts each `FieldError` into a `FieldErrorDto` with corporate-toned text (also sourced from `messages_en.properties`, not the default Bean Validation wording, which reads as developer-facing).

### M.7 Rules

- No controller, service, or repository ever returns a raw `String` message, a raw exception message, or a Mongo/HTTP client exception message directly to the client.
- Every new error condition gets a `MessageCode` added to the enum + a properties entry **before** the code that throws it is written — message hub is designed first, not retrofitted.
- Upstream errors (Odoo 4xx/5xx, network failures) are always translated through a code (`ODOO_UPSTREAM_ERROR`, `CONNECTION_TEST_FAILED_UNREACHABLE`, etc.) — the raw upstream body is logged with the `traceId`, never forwarded to the client.
- Log lines always include `code` + `traceId`; response bodies always include `traceId` — this pair is how support reproduces an issue from a user's screenshot without needing the full request replayed.

---

## Q. Pagination, Sorting & Search — Shared Standard

**Goal:** every list endpoint (`GET /connections`, `GET /applications`, `GET /connectors`, and every future list endpoint) behaves identically, is backed by proper indexes, and never lets an unbounded or unindexed query reach Mongo.

### Q.1 Request Contract — One Shape for Every List Endpoint

```java
public class PageQueryRequestDto {
    private Integer page = 0;          // 0-based
    private Integer size = 20;         // capped server-side, see Q.4
    private String sortBy;             // must be in that module's SortableFieldRegistry allowlist
    private String sortDir = "DESC";   // ASC | DESC
    private String search;             // free-text — matched against that module's searchable fields
    private Map<String, String> filters; // exact-match filters, e.g. { "applicationId": "APP-ODOO", "status": "ACTIVE" }
}
```

Bound via `@ModelAttribute`/query params on every list endpoint, e.g.:
```
GET /connections?page=0&size=20&sortBy=createdAt&sortDir=DESC&search=odoo&status=ACTIVE&applicationId=APP-ODOO
```
Same param names across every module — the frontend's data-table component is built once and reused, not rebuilt per screen.

### Q.2 Response Contract — One Shape for Every List Endpoint

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request completed successfully.",
  "data": {
    "items": [ { "...": "ConnectionSummaryResponseDto" } ],
    "page": { "page": 0, "size": 20, "totalElements": 47, "totalPages": 3, "hasNext": true, "hasPrevious": false }
  },
  "traceId": "f1a2b3c4",
  "timestamp": "2026-08-15T10:05:00Z"
}
```
`PagedResponseDto<T> { List<T> items; PageMetaResponseDto page; }` is the one generic wrapper reused by every module — no bespoke "connections list response" shape.

### Q.3 Sorting — Allowlisted, Never Free-Text-to-Field

```java
@Component
public class SortableFieldRegistry {

    // Why: accepting an arbitrary client-supplied field name as a Mongo sort key is both a
    // performance risk (sort on an unindexed field) and a minor information-exposure risk
    // (reveals internal field names). Every module publishes its own small allowlist instead.
    private static final Map<String, String> CONNECTION_SORT_FIELDS = Map.of(
            "name", "name",
            "status", "status",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "lastTestedAt", "lastTestedAt"
    );

    public String resolve(String module, String requestedField, String defaultField) {
        Map<String, String> allowlist = allowlistFor(module);
        if (requestedField == null || !allowlist.containsKey(requestedField)) return defaultField;
        return allowlist.get(requestedField);
    }
}
```
Every sortable field in every allowlist **must have a matching Mongo index** (see Q.6) — the registry and the index list in `MongoConfig` are reviewed together whenever a field is added.

### Q.4 Pagination — Hard Caps, Consistent Defaults

- Default `size = 20`, **hard server-side cap `size <= 100`** regardless of what the client requests (already shown in `MongoQueryTemplates.toPageable`, Section 6.4) — prevents an accidental or malicious "load everything" call.
- `page` below `0` or `size` below `1` is corrected to the default, not rejected as a validation error — list endpoints should degrade gracefully rather than 400 on a minor client bug.
- Total counts (`totalElements`) are computed via `mongoTemplate.count()` on the **same filter Criteria** as the data query — count and data must never drift by using different filters.
- For very large collections in later milestones (`integration_executions`, `execution_failures`), prefer cursor-based pagination (`lastId`/`createdAt` watermark) over `skip()`, since Mongo `skip()` degrades linearly with offset — flagged as a forward-looking note, not required for the small catalog collections in this plan (`applications`, `connectors`, `connection_configurations`).

### Q.5 Search — Centralized, Indexed, Bounded

```java
@Component
public class SearchSpecificationBuilder {

    // Why: one method builds the "search" clause for every module from a per-module list of
    // searchable fields, so search behavior (case-insensitivity, partial match) never diverges
    // between screens, and no module hand-rolls its own regex.
    public Criteria build(String searchTerm, List<String> searchableFields) {
        if (!StringUtils.hasText(searchTerm) || searchableFields.isEmpty()) return new Criteria();
        String safe = Pattern.quote(searchTerm.trim()); // escape regex metacharacters from user input
        Criteria[] fieldMatches = searchableFields.stream()
                .map(field -> Criteria.where(field).regex(safe, "i"))
                .toArray(Criteria[]::new);
        return new Criteria().orOperator(fieldMatches);
    }
}
```
- Connections module searchable fields: `name`, `endpoint.baseUrl` — deliberately **not** `credentialReference` or anything secret-adjacent.
- For collections expected to grow large and need real free-text relevance ranking (later milestones — execution logs, audit search), switch that specific module to a MongoDB **Atlas Search / text index** instead of regex; regex search is fine at the scale of `connections`/`applications`/`connectors` (tens to low-thousands of documents per tenant) but not appropriate for high-volume collections. This is a per-module decision, not a blanket rule — call it out in that module's design doc when reached.

### Q.6 Required Indexes for Smooth List Queries (in addition to Section 6.1's uniqueness indexes)

| Collection | Index | Serves |
|---|---|---|
| `connection_configurations` | `{ tenantId: 1, status: 1, createdAt: -1 }` | Default list view (tenant-scoped, status filter, newest-first) |
| `connection_configurations` | `{ tenantId: 1, name: 1 }` | Search-by-name + duplicate-name check (`CONNECTION_DUPLICATE_NAME`) |
| `connection_configurations` | `{ tenantId: 1, applicationId: 1, status: 1 }` | Filtered list (`?applicationId=...&status=...`) |
| `applications` | `{ status: 1, name: 1 }` | Catalog listing, active-only default filter |
| `connectors` | `{ applicationId: 1, status: 1 }` | Registry lookup + listing by application |

All created in `MongoConfig` on startup (idempotent `ensureIndex` calls), never left to be created ad hoc in production.

### Q.7 One Reusable Pattern, Not Per-Endpoint Code

End-to-end, a list endpoint's controller method is intentionally thin — all the pagination/sorting/search decision-making lives in the shared kernel (Q.1–Q.6), not duplicated per module:
```java
@GetMapping
public ApiResponse<PagedResponseDto<ConnectionSummaryResponseDto>> list(
        @ModelAttribute PageQueryRequestDto query,
        @RequestParam(required = false) String applicationId,
        @RequestParam(required = false) String status) {
    var criteria = new ConnectionSearchCriteria(applicationId, status);
    var page = connectionService.search(TenantContext.current(), criteria, query);
    return apiMessageResponseBuilder.success(MessageCode.SUCCESS, Map.of(), page);
}
```
`connectionService.search(...)` delegates straight to `ConnectionCustomRepositoryImpl` (Section 6.4), which now also calls `SearchSpecificationBuilder` and `SortableFieldRegistry` alongside the tenant/filter Criteria already shown — same one method, three shared building blocks, no bespoke logic per module.

---

## 10. Roadmap Beyond This Plan (Reference Only — Not Built Now)

Milestone 3 — Mapping/Transformation/Preview/Publish (`integrations`, `integration_versions`) · Milestone 4 — Execution Engine, Outbox, Redis Streams, Retry/DLQ, Idempotency, Incremental checkpoints (`integration_executions`, `execution_failures`, `entity_identity_map`) · Milestone 5 — Inbound Webhooks (`webhook_endpoints`) · Milestone 6 — Generic REST/OpenAPI connector (drops into the same `connector/spi` with zero core-engine change) · Milestone 7 — Security hardening, load testing, production readiness.

This plan's package structure and SPI contract are deliberately shaped so each of these slots in without refactoring Sections 3–7.
