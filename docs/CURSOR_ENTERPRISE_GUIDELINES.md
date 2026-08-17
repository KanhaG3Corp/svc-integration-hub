# Cursor Rules --- Enterprise SaaS Development Guidelines & Policy

## 1. Purpose

This document defines the mandatory engineering guidelines and policies
that must be followed when modifying, creating, or reviewing code in
this project using Cursor.

The application is an **enterprise-grade SaaS product**. All
implementation decisions must prioritize:

-   Reliability
-   Scalability
-   Performance
-   Security
-   Maintainability
-   Readability
-   Backward compatibility
-   Operational stability
-   Future extensibility

Cursor must behave as an engineering assistant, not as an autonomous
architect. It must not make major architectural or business decisions
without human confirmation.

------------------------------------------------------------------------

# 2. Core Engineering Principles

## 2.1 Enterprise SaaS Standard

Treat every implementation as production-grade enterprise software.

Code must be designed with consideration for:

-   Multiple tenants
-   Concurrent users and requests
-   High data volume
-   High availability
-   Horizontal scalability
-   Failure scenarios
-   Security boundaries
-   Observability
-   Future feature growth
-   Backward compatibility
-   Operational and deployment impact

Do not implement a solution merely because it works for the current use
case.

The solution must also be appropriate for a production SaaS environment.

------------------------------------------------------------------------

## 2.2 Performance Is a Top Priority

Performance must be considered before implementation, not after
implementation.

Always evaluate:

-   Database query cost
-   MongoDB aggregation complexity
-   Number of database calls
-   Number of external API calls
-   Network latency
-   CPU consumption
-   Memory consumption
-   Object creation
-   Collection sizes
-   Pagination strategy
-   Sorting and filtering
-   Serialization/deserialization
-   Synchronous versus asynchronous processing
-   Cache usage
-   Potential concurrency issues

Prefer solutions that reduce:

-   Database round trips
-   Network calls
-   Unnecessary loops
-   Repeated calculations
-   Duplicate processing
-   Large object creation
-   Unnecessary data transfer
-   Full collection scans
-   Repeated aggregation stages
-   Unnecessary API calls

Do not optimize blindly. Prefer measurable, understandable optimizations
that preserve correctness.

------------------------------------------------------------------------

# 3. Code Quality Standards

## 3.1 Clean, Clear, and Simple Code

Code must be:

-   Clean
-   Clear
-   Simple
-   Readable
-   Predictable
-   Easy to debug
-   Easy to test
-   Easy for another developer to enhance

Prefer straightforward code over clever code.

Avoid:

-   Deeply nested conditions
-   Extremely long methods
-   Complex one-liners
-   Unnecessary streams
-   Excessive lambda usage
-   Hidden side effects
-   Duplicate logic
-   Magic values
-   Unclear abstractions
-   Premature optimization
-   Overly generic utility classes

The implementation should be understandable without requiring the
original developer to explain it.

------------------------------------------------------------------------

## 3.2 Do Not Over-Engineer

Do not create unnecessary:

-   Files
-   Folders
-   Classes
-   Interfaces
-   Abstract classes
-   Design patterns
-   Utility layers
-   Wrappers
-   DTOs
-   Services
-   Repositories
-   Configuration classes

Create an abstraction only when it provides a real benefit such as:

-   Reusability
-   Separation of responsibility
-   Testability
-   Extensibility
-   Clear domain boundaries
-   Removal of meaningful duplication

Do not introduce an abstraction merely because it is theoretically
possible.

------------------------------------------------------------------------

## 3.3 Follow Existing Project Structure

Before making changes:

1.  Inspect the existing project structure.
2.  Identify the established package structure.
3.  Identify existing naming conventions.
4.  Identify existing architectural patterns.
5.  Identify similar implementations.
6.  Reuse established conventions where appropriate.

Do not introduce a new structure when the existing project already
provides a suitable pattern.

Existing conventions take priority unless there is a confirmed reason to
change them.

------------------------------------------------------------------------

# 4. SOLID Principles

All code should follow SOLID principles where applicable.

## Single Responsibility Principle

Each class and method should have a clear responsibility.

Avoid classes that simultaneously handle:

-   Business logic
-   Database access
-   External API communication
-   Transformation
-   Validation
-   Response construction

Separate responsibilities when doing so improves clarity and
maintainability.

## Open/Closed Principle

Design components so that future supported extensions can be added
without unnecessarily modifying stable existing behavior.

## Liskov Substitution Principle

Implementations must respect the contracts of their abstractions.

## Interface Segregation Principle

Do not create large interfaces containing unrelated responsibilities.

## Dependency Inversion Principle

High-level business logic should not unnecessarily depend directly on
low-level implementation details.

Use existing dependency injection and architectural conventions.

Do not introduce interfaces solely to satisfy SOLID mechanically.

------------------------------------------------------------------------

# 5. CQRS Policy

Where CQRS is already established or explicitly required, maintain a
clear separation between:

-   Command operations --- create/update/delete/state-changing
    operations
-   Query operations --- read-only operations

Query implementations must be optimized for read performance.

Command implementations must focus on:

-   Validation
-   Business rules
-   State transitions
-   Consistency
-   Side effects

Do not mix complex database querying directly into business logic.

Do not introduce CQRS into an unrelated area merely for architectural
style. Follow the existing project architecture and confirm major
structural changes with a human.

------------------------------------------------------------------------

# 6. MongoDB and Database Policy

## 6.1 Repository Separation

MongoDB queries, aggregation pipelines, repository-specific database
logic, and persistence operations must remain inside the appropriate
repository/custom repository layer or repository folder.

Do **not** mix database queries directly inside:

-   Controllers
-   Business services
-   Domain logic
-   Utility classes
-   DTOs

Business logic should not contain raw MongoDB query construction unless
the existing architecture explicitly requires it and the implementation
has been approved.

------------------------------------------------------------------------

## 6.2 MongoDB Query Standards

Every MongoDB query must be evaluated for:

-   Index usage
-   Filter selectivity
-   Projection
-   Pagination
-   Sort performance
-   Aggregation complexity
-   Collection size
-   Memory usage
-   Network payload
-   Number of returned documents

Avoid:

-   Full collection scans where avoidable
-   Returning unnecessary fields
-   Fetching complete documents when only a few fields are needed
-   Repeated database calls for the same data
-   N+1 query patterns
-   Unnecessary `$lookup`
-   Unnecessary `$unwind`
-   Unbounded queries
-   Unbounded result sets
-   Expensive calculations that can be avoided

Use projections when only a subset of fields is required.

Use pagination for potentially large datasets.

------------------------------------------------------------------------

## 6.3 Aggregation Pipeline Standards

Aggregation pipelines must be designed carefully.

Prefer:

1.  Early filtering
2.  Early projection where appropriate
3.  Indexed match conditions
4.  Minimal document expansion
5.  Minimal `$lookup`
6.  Minimal `$unwind`
7.  Required stages only

Do not add aggregation stages without a clear purpose.

Avoid loading large datasets into application memory when MongoDB can
efficiently perform the required operation.

Complex aggregation pipelines should be reviewed for execution cost and
index compatibility.

------------------------------------------------------------------------

## 6.4 Repository Responsibility

Repository/custom repository code is responsible for:

-   Query construction
-   Aggregation pipelines
-   Persistence operations
-   Database-specific optimizations
-   Query projections
-   Database pagination
-   Database-specific mapping where required

Service/business layers are responsible for:

-   Business rules
-   Orchestration
-   Validation
-   Domain decisions
-   Transaction boundaries where applicable
-   Calling repositories

Keep these responsibilities separated.

------------------------------------------------------------------------

# 7. API and Service Layer Policy

APIs must be:

-   Predictable
-   Consistent
-   Efficient
-   Secure
-   Backward compatible

Before adding or changing an API, evaluate:

-   Existing consumers
-   Request/response compatibility
-   Validation
-   Error handling
-   Authentication/authorization
-   Performance
-   Pagination
-   Idempotency where applicable
-   Logging
-   Observability

Do not break existing API contracts without explicit approval.

Avoid unnecessary synchronous service-to-service calls.

Avoid repeated calls to the same downstream service.

If multiple downstream calls are required, evaluate whether they can be:

-   Combined
-   Cached
-   Parallelized safely
-   Replaced by existing data
-   Moved to asynchronous processing

------------------------------------------------------------------------

# 8. Frontend Policy

## 8.1 Common Components

Do **not** modify shared/common frontend components without human
approval.

This includes, but is not limited to:

-   Common buttons
-   Common tables
-   Common forms
-   Common modals
-   Common layouts
-   Common filters
-   Common input components
-   Shared hooks
-   Shared utilities
-   Shared state management
-   Common API clients
-   Global styles

If a common component must be changed, Cursor must first ask for
permission and clearly explain:

1.  Why the change is required.
2.  Which components/features will be affected.
3.  What behavior will change.
4.  What regression risks exist.
5.  Whether a local/module-level alternative is possible.

Do not silently modify shared components to solve a local feature
requirement.

------------------------------------------------------------------------

# 9. Naming Standards

Every file, class, method, variable, constant, enum, and API model must
have a meaningful name.

Names should communicate purpose.

Avoid names such as:

-   `data`
-   `obj`
-   `temp`
-   `test`
-   `value`
-   `result`
-   `process`
-   `handle`
-   `common`
-   `manager`

unless the context makes the meaning genuinely clear.

Prefer names that explain intent.

Examples:

-   `contractExpiryThresholdDays`
-   `findActiveVendorContracts`
-   `calculateAssessmentCompletion`
-   `loadWorkflowDefinition`

Follow the project's existing naming conventions for:

-   Packages
-   Classes
-   Interfaces
-   Methods
-   Variables
-   Constants
-   MongoDB collections
-   Fields
-   API endpoints

------------------------------------------------------------------------

# 10. Comments and Documentation

Important code blocks must contain comments explaining:

### What

What is the code doing?

### Why

Why is the code required?

Comments should explain intent and business/technical reasoning rather
than simply repeating the code.

Good:

``` text
// Load only the fields required by the grid to reduce MongoDB payload
// and avoid transferring large vendor documents for every row.
```

Avoid comments such as:

``` text
// Get vendor
```

when the code already clearly communicates that.

Do not add excessive comments to obvious code.

------------------------------------------------------------------------

# 11. Error Handling

Error handling must be:

-   Consistent
-   Meaningful
-   Safe
-   Observable
-   Appropriate for the failure

Do not:

-   Swallow exceptions
-   Use empty catch blocks
-   Return misleading success responses
-   Log sensitive information
-   Expose internal implementation details to clients

Preserve the existing error-handling architecture.

If introducing a new exception type or error-handling mechanism
represents a significant architectural change, request human approval
first.

------------------------------------------------------------------------

# 12. Security Policy

Security must be considered for every implementation.

Always evaluate:

-   Authentication
-   Authorization
-   Tenant isolation
-   Input validation
-   Output exposure
-   Sensitive data
-   Secrets
-   Logging
-   API access
-   Injection risks
-   Access control
-   Data leakage

Never expose:

-   Passwords
-   Tokens
-   Secrets
-   API keys
-   Credentials
-   Sensitive tenant data

Do not weaken existing security controls to make implementation easier.

Any major security change requires human confirmation.

------------------------------------------------------------------------

# 13. Multi-Tenant SaaS Safety

Because this is a SaaS application, tenant isolation must never be
compromised.

Any implementation involving data access must verify:

-   Correct tenant context
-   Correct database/collection
-   Correct authorization
-   Correct tenant-specific configuration
-   No cross-tenant data leakage

Do not assume tenant context when it affects data access.

Do not bypass existing tenant resolution mechanisms.

Any architectural change affecting tenant isolation requires explicit
human approval.

------------------------------------------------------------------------

# 14. Backward Compatibility

Existing functionality must not be unnecessarily impacted.

Before changing existing code:

1.  Understand its current behavior.
2.  Identify its consumers.
3.  Identify dependent APIs/components.
4.  Identify possible side effects.
5.  Preserve existing behavior unless change is explicitly required.

Do not refactor large areas of the application while implementing a
small feature unless the refactor is necessary.

If a refactor is required, explain the reason and impact before making a
major change.

------------------------------------------------------------------------

# 15. Change Scope Control

Implement only what is required.

Do not automatically:

-   Refactor unrelated code
-   Rename unrelated files
-   Change unrelated APIs
-   Reformat large parts of the project
-   Upgrade dependencies
-   Change architecture
-   Replace libraries
-   Change shared components
-   Change database models

unless explicitly requested or approved.

A feature implementation should remain focused on its intended scope.

------------------------------------------------------------------------

# 16. Human Approval Policy

Cursor must **stop and ask for human confirmation** before making any
major decision involving:

-   Architecture
-   Database schema
-   Database strategy
-   Tenant isolation
-   Security model
-   Authentication/authorization
-   Shared frontend components
-   Public API contracts
-   Major API behavior
-   Messaging architecture
-   Event architecture
-   Caching architecture
-   Infrastructure
-   Deployment
-   CI/CD
-   Dependency replacement
-   Major library upgrades
-   Major performance trade-offs
-   Data migration
-   Data deletion
-   Breaking changes
-   Major refactoring
-   Business rules
-   Workflow behavior
-   Any change that can affect multiple modules

Do not assume approval.

Do not interpret silence as approval.

When approval is required, provide:

### Proposed Change

What will be changed?

### Reason

Why is the change required?

### Impact

Which modules/files/features may be affected?

### Alternatives

What simpler or safer alternatives were considered?

### Recommendation

What does Cursor recommend and why?

Then wait for human confirmation.

------------------------------------------------------------------------

# 17. No Hard Decisions Without Human Intervention

Cursor must not independently decide business behavior when requirements
are ambiguous.

Examples:

-   What a status should mean
-   Which workflow transition should happen
-   Whether a record should be deleted
-   Which field should become mandatory
-   Which security rule should apply
-   Which tenant should receive data
-   Which API behavior should be changed
-   Which business threshold should be used
-   Which database model should be introduced

If requirements are unclear:

1.  Identify the ambiguity.
2.  Explain the possible interpretations.
3.  Recommend the safest option if useful.
4.  Ask the human to decide.

Never silently choose a business rule.

------------------------------------------------------------------------

# 18. Performance Optimization Checklist

Before finalizing implementation, evaluate:

-   [ ] Can a database call be removed?
-   [ ] Can multiple database calls be combined?
-   [ ] Is the query using the correct indexes?
-   [ ] Is only required data being fetched?
-   [ ] Is pagination implemented where necessary?
-   [ ] Is there an N+1 query pattern?
-   [ ] Can repeated calculations be avoided?
-   [ ] Can unnecessary loops be removed?
-   [ ] Can object creation be reduced?
-   [ ] Can API calls be reduced?
-   [ ] Can existing cache be reused?
-   [ ] Is the response payload unnecessarily large?
-   [ ] Can processing be moved to asynchronous execution safely?
-   [ ] Could this create a memory or CPU problem under high load?
-   [ ] Could this become a bottleneck as tenant/data volume grows?

------------------------------------------------------------------------

# 19. Implementation Workflow

Before writing code, Cursor should follow this process:

## Step 1 --- Understand

Inspect:

-   Existing code
-   Existing architecture
-   Related classes
-   Related APIs
-   Related repositories
-   Existing frontend components
-   Existing tests
-   Existing configuration

## Step 2 --- Identify

Determine:

-   Exact requirement
-   Existing behavior
-   Required change
-   Dependencies
-   Risks
-   Performance considerations

## Step 3 --- Plan

Create a small implementation plan.

The plan should identify:

-   Files to modify
-   Files to create
-   Database changes
-   API changes
-   Frontend changes
-   Testing requirements

Avoid creating files unless necessary.

## Step 4 --- Check for Approval

If the change involves a major architectural, business, database,
security, or shared-component decision, stop and request human approval.

## Step 5 --- Implement

Implement the smallest clean solution that satisfies the requirement.

## Step 6 --- Validate

Check:

-   Compilation
-   Tests
-   Existing functionality
-   Error handling
-   Performance
-   Security
-   Tenant isolation
-   Backward compatibility

## Step 7 --- Report

Clearly explain:

-   What changed
-   Why it changed
-   Files changed
-   Important implementation decisions
-   Performance considerations
-   Testing performed
-   Any remaining risks

------------------------------------------------------------------------

# 20. Testing Standards

Every meaningful change should consider appropriate testing.

Tests should cover, where applicable:

-   Happy path
-   Validation failures
-   Edge cases
-   Error scenarios
-   Authorization
-   Tenant isolation
-   Business rules
-   Database behavior
-   API behavior
-   Regression scenarios

Do not remove existing tests simply because they fail after a change.

Understand the failure first.

------------------------------------------------------------------------

# 21. Maintainability and Future Enhancements

Code should be structured so future developers can extend it without
unnecessary rewriting.

Prefer:

-   Clear responsibilities
-   Stable contracts
-   Small focused methods
-   Reusable logic where genuinely needed
-   Consistent patterns
-   Meaningful naming
-   Minimal coupling

Avoid designing for hypothetical requirements that have no current
business justification.

Build for realistic future extension, not unlimited theoretical
flexibility.

------------------------------------------------------------------------

# 22. Decision Priority

When multiple implementation options are available, evaluate them in
this order:

1.  Correctness
2.  Security
3.  Tenant isolation
4.  Reliability
5.  Performance
6.  Scalability
7.  Maintainability
8.  Simplicity
9.  Extensibility
10. Developer convenience

Never sacrifice correctness or security for convenience.

Prefer a solution that is:

**Simple + Optimized + Reliable + Maintainable**

over:

**Complex + Over-engineered + Difficult to maintain**

------------------------------------------------------------------------

# 23. Cursor Behavioral Rules

Cursor must follow these rules at all times:

-   Do not make major decisions without human confirmation.
-   Do not assume ambiguous business requirements.
-   Do not modify common frontend components without permission.
-   Do not mix MongoDB queries with business logic.
-   Do not introduce unnecessary abstractions.
-   Do not over-engineer.
-   Do not change unrelated code.
-   Do not break existing functionality unnecessarily.
-   Do not ignore performance implications.
-   Do not introduce inefficient database access.
-   Do not create unnecessary files.
-   Do not duplicate existing functionality when reusable functionality
    already exists.
-   Do not silently change public API behavior.
-   Do not weaken security.
-   Do not bypass tenant isolation.
-   Do not hide errors.
-   Do not add dependencies unless necessary and approved when the
    impact is significant.
-   Follow the existing project structure and conventions.
-   Prefer the simplest production-ready implementation.
-   Ask before making a major architectural or business decision.

------------------------------------------------------------------------

# 24. Definition of Done

A change is considered complete only when:

-   [ ] The requested functionality is implemented.
-   [ ] Existing project conventions are followed.
-   [ ] Code is clean and understandable.
-   [ ] SOLID principles are respected where applicable.
-   [ ] Database logic is isolated in the repository/custom repository
    layer.
-   [ ] CQRS conventions are followed where applicable.
-   [ ] Performance has been considered.
-   [ ] Scalability has been considered.
-   [ ] Security has been considered.
-   [ ] Tenant isolation has been verified.
-   [ ] Existing functionality has not been unnecessarily impacted.
-   [ ] Relevant tests are added or updated.
-   [ ] Error handling is appropriate.
-   [ ] Important code blocks contain meaningful comments.
-   [ ] No unnecessary files or abstractions were introduced.
-   [ ] No major decision was made without human approval.
-   [ ] The implementation is maintainable for future developers.

------------------------------------------------------------------------

# 25. Final Rule

**Do not optimize for writing code quickly. Optimize for building a
reliable, secure, scalable, high-performance, enterprise-grade SaaS
product that another developer can easily understand and maintain.**

When uncertain, **stop, explain the uncertainty, provide the available
options, and ask the human before making the decision.**
