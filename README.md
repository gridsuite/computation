# computation

[![Actions Status](https://github.com/gridsuite/computation/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/computation/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Acomputation&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Acomputation&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

**gridsuite-computation** is a shared Java library of the [GridSuite](https://github.com/gridsuite) platform providing **common infrastructure for computation services**.

It provides the following capabilities:

- **Template-based computation lifecycle** via abstract base classes covering network loading, execution, result persistence, and notification.
- **Asynchronous run/cancel pipeline** using Spring Cloud Stream: any computation server can publish and consume run/cancel/result messages out-of-the-box.
- **Transactional notification safety** via the `@PostCompletion` annotation, guaranteeing that result messages are only sent after the database transaction has committed.
- **Flexible result filtering** with a JPA Criteria API layer (`SpecificationUtils`, `AbstractCommonSpecificationBuilder`) supporting text, numeric, and IN-clause filters with sorting and pagination.
- **Network equipment filtering** bridging the `filter-server` REST API to runtime `Network` objects (nominal voltage, country codes, substation properties, expert filters).
- **Optional S3 debug support**: computation workers can archive their working directories to AWS S3 for post-mortem analysis.
- **Micrometer observability** tracking computation count and concurrency by provider, type, and status.

---

## Technical Stack

- Spring Boot (Data JPA, Cloud Stream, Auto-configuration)
- Spring Cloud AWS / AWS SDK v2 (optional S3 support)
- Spring Cloud Stream / RabbitMQ
- AspectJ (for `@PostCompletion` AOP)
- Micrometer / OpenTelemetry
- Powsybl Core (Network, LocalComputationManager, ReportNode)
- Jakarta Persistence (JPA Criteria API)

---

## How to Use in a Computation Server

### 1. Add the dependency

```xml
<dependency>
    <groupId>org.gridsuite</groupId>
    <artifactId>gridsuite-computation</artifactId>
</dependency>
```

The version is managed by the parent BOM (`powsybl-ws-dependencies`).

### 2. Implement the abstract classes

Each computation server must extend the following classes:

| Abstract class | What to implement |
|---|---|
| `AbstractComputationRunContext<P>` | Add server-specific parameters `P` and additional context fields |
| `AbstractResultContext<C>` | Override `getSpecificMsgHeaders()` to add custom message headers |
| `AbstractComputationResultService<S>` | Implement `insertStatus`, `delete`, `deleteAll`, `findStatus` using your JPA repositories |
| `AbstractComputationService<C, T, S>` | Implement `getProviders()` and `runAndSaveResult(C)` |
| `AbstractWorkerService<R, C, P, S>` | Implement `fromMessage()`, `saveResult()`, `getComputationType()`, `getCompletableFuture()` |
| `AbstractComputationObserver<R, P>` | Implement `getComputationType()` and `getResultStatus(R)` |
| `AbstractFilterService` | Provide the `RestClient` URI for the `filter-server` |
| `AbstractCommonSpecificationBuilder<T>` | Implement `isNotParentFilter()`, `getIdFieldName()`, `getResultIdPath()` |

### 3. Configure messaging channels

Declare the Spring Cloud Stream bindings in your `application.yaml`:

```yaml
spring:
  cloud:
    stream:
      bindings:
        consumeRun-in-0:
          destination: <computation-type>.run
        consumeCancel-in-0:
          destination: <computation-type>.cancel
        publishResult-out-0:
          destination: <computation-type>.result
        publishStopped-out-0:
          destination: <computation-type>.stopped
```

---

## Asynchronous Computation Flow

```
REST Controller
      │
      ▼
AbstractComputationService.runAndSaveResult()
      │  publishes on <type>.run
      ▼
AbstractWorkerService.consumeRun()
  ├── loads Network from network-store-server
  ├── calls getCompletableFuture()  ←── server-specific computation
  ├── sends report to report-server
  ├── calls saveResult()            ←── server-specific persistence
  └── publishes result on <type>.result
                                           │
AbstractWorkerService.consumeCancel() ─────┘
  └── cancels the CompletableFuture via lockRunAndCancel
```

---

## @PostCompletion — Transactional Notification Safety

The `@PostCompletion` annotation ensures that any method annotated with it is executed **only after the current database transaction has completed** (whether committed or rolled back). This prevents race conditions where a consumer receives a result notification before the result record is visible in the database.

```java
// In NotificationService
@PostCompletion
public void sendResultMessage(UUID resultUuid, String receiver) {
    streamBridge.send("publishResult-out-0", buildResultMessage(resultUuid, receiver));
}
```

If no transaction is active when the method is called, it executes immediately.

---

## Result Filtering

`SpecificationUtils` provides JPA `Specification` factories dispatched from a `List<ResourceFilterDTO>`:

| Filter type | Supported operations |
|---|---|
| `TEXT` | `CONTAINS`, `STARTS_WITH`, `EQUALS`, `NOT_EQUAL`, `IN` |
| `NUMBER` | `EQUALS`, `NOT_EQUAL`, `LESS_THAN_OR_EQUAL`, `GREATER_THAN_OR_EQUAL` |

Nested field paths (dot-separated) and chunked `IN` clauses (max 10 000 items) are supported out of the box.

```java
// Build filters (e.g. parsed from a JSON query param)
List<ResourceFilterDTO> filters = List.of(
    new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.STARTS_WITH, "VL", "voltageLevelId"),
    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.LESS_THAN_OR_EQUAL, 400.0, "nominalVoltage")
);

// Combine with a base specification and query the repository
Specification<MyResultEntity> spec = SpecificationUtils.appendFiltersToSpecification(
    Specification.where(null), filters);

Page<MyResultEntity> page = myResultRepository.findAll(spec, pageable);
```

- `column` supports dot-separated nested paths (e.g. `"fortescueCurrent.positiveMagnitude"`).
- `tolerance` on `NUMBER` filters defaults to half a unit of the last decimal digit in the filter value if not provided.

---

## Optional S3 Debug Support

When `computation.s3.enabled=true`, the worker service zips its working directory after a computation and uploads it to the configured S3 bucket. The S3 key is stored in the database and can later be downloaded via the computation service.

```yaml
computation:
  s3:
    enabled: true

spring:
  cloud:
    aws:
      bucket: ws-bucket
```

---

## Interactions with Other Services

```
┌────────────────────────┐
│  gridsuite-computation │──► network-store-server  (load Network objects)
│        (library)       │──► filter-server          (resolve equipment filters)
│                        │──► report-server          (post computation logs)
└────────────────────────┘
         ▲  ▼
      RabbitMQ (<type>.run / <type>.cancel / <type>.result / <type>.stopped)
```

---

