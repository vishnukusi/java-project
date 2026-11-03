# SentinelCV: Java Fundamentals & Architecture Concepts Mapping

## 1. Overview
SentinelCV was engineered as an academic demonstration of modern, robust, and idiomatic Java 21 development. Core language constructs, object-oriented principles, design patterns, and platform libraries are utilized naturally throughout the codebase.

---

## 2. Concept Mapping Table

| Java Concept / Principle | Source Implementation in SentinelCV | Detailed Technical Description |
| :--- | :--- | :--- |
| **Strategy Pattern & Polymorphism** | `com.sentinelcv.dataset.DatasetParser`<br>`CocoParser.java`<br>`YoloParser.java` | The `DatasetParser` interface defines `canParse(File)` and `parse(File)`. The Spring container injects `List<DatasetParser>` into `DatasetAuditService`, which uses dynamic method dispatch to select the appropriate parser strategy at runtime. |
| **Strategy Pattern (Model Loading)**| `com.sentinelcv.model.ModelLoader`<br>`OnnxModelLoader.java`<br>`TorchScriptModelLoader.java` | Extensible abstraction for deep learning frameworks. ONNX runtime engine and PyTorch TorchScript loaders share the uniform `ModelLoader` contract. |
| **Builder Pattern & Encapsulation** | `com.sentinelcv.entity.SecurityFinding` | `SecurityFinding.Builder` provides fluent instantiation of findings with strict validation rules preventing creation of findings without finding IDs, module names, or severities. |
| **Java Collections Framework** | `com.sentinelcv.dataset.DuplicateDetector`<br>`AnnotationValidator.java` | Uses `HashMap` for $O(1)$ SHA-256 hash collision grouping; `HashSet` for tracking visited near-duplicate indices; `ArrayList` for ordered sequence in inference verification; and `PriorityQueue` / `Collections.sort` for severity-ordered finding prioritization. |
| **Java NIO & Safe Path Traversal** | `com.sentinelcv.util.ArchiveUtil` | Uses `java.nio.file.Path`, `Files.walk()`, and path normalization (`targetDirectory.resolve(entry.getName()).normalize()`) with magic byte verification (`PK\x03\x04`) and atomic rollback to prevent Zip-Slip and symlink attacks. |
| **Streaming File Hashing** | `com.sentinelcv.util.HashUtil` | Implements buffered chunked streaming (`BufferedInputStream` with 8KB buffer) into `MessageDigest.update()`. Avoids reading multi-gigabyte models into heap memory, preventing OutOfMemory (OOM) errors. |
| **Java Cryptography Architecture (JCA)** | `com.sentinelcv.util.CryptoUtil`<br>`ProvenanceKeyManager.java` | Demonstrates JCA usage: `MessageDigest` (SHA-256), `Mac` (`HmacSHA256`), `KeyPairGenerator` (RSA-2048), `Signature` (`SHA256withRSA`), `SecureRandom`, and externalized key management avoiding hardcoded secrets. |
| **Binary Protocol Encoding (Protobuf)** | `com.sentinelcv.util.FixtureGenerator` | Pure Java binary Protocol Buffer encoder generating valid ONNX computational graphs (varint encoding, wire types, IEEE 754 float bit conversion, nested submessages) without protoc dependency. |
| **Custom Exception Hierarchy** | `com.sentinelcv.exception.*` | Custom unchecked exception hierarchy rooted in `SentinelCvException`, specialized into `DatasetParsingException`, `ModelLoadException`, and `ProvenanceException`, handled centrally via `@ControllerAdvice`. |
| **Multithreading & Concurrency** | `com.sentinelcv.config.ApplicationConfig`<br>`DatasetAuditService.java` | Dedicated `ThreadPoolTaskExecutor` bean named `auditTaskExecutor` with thread naming (`AuditWorker-`) and bounds for Spring AOP `@Async` audit jobs returning non-blocking `CompletableFuture`. |
| **Numerical Stability & Overflow Protection** | `BehavioralAnalyzer.java`<br>`StatisticalUtil.java` | Uses `Math.multiplyExact` to prevent integer overflow when calculating dynamic tensor dimensions, and `Double.isFinite()` to sanitize `NaN`/`Infinity` before statistical divergence calculations. |
| **Java Streams & Lambdas** | `DatasetStatisticsService.java`<br>`DistributionComparator.java` | Uses declarative stream pipelines (`filter`, `map`, `reduce`, `collect`, `groupingBy`) for calculating class imbalances, averages, and finding counts. |
| **Modern Java 21 Enums & Switches** | `com.sentinelcv.enums.*` | Enums with state and methods (`FindingSeverity`, `AssessmentDecision`, `AuditStatus`). Enhanced `switch` expressions with pattern matching used in risk score deduction. |
| **Spring JDBC & SQLite Persistence** | `com.sentinelcv.repository.*` | Demonstrates pure JDBC row mapping (`RowMapper<T>`), parameterized `PreparedStatement` to prevent SQL injection, `GeneratedKeyHolder` for auto-incremented primary keys, and WAL journal mode. |

