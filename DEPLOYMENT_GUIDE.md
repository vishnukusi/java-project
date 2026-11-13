# SentinelCV: Deployment & Operation Guide

## 1. Quick Startup Instructions

### 1.1 Recommended: Docker Desktop (macOS / Linux / Windows)
SentinelCV enforces fail-closed ZIP archive security using Java NIO `SecureDirectoryStream` (`ArchiveUtil`), which macOS `APFS` / `HFS+` and macOS VirtioFS bind mounts do not support. Running SentinelCV inside its Linux container with the named Docker volume (`sentinelcv_data`) provides full `SecureDirectoryStream` support, POSIX `FileLock` isolation, and SQLite `WAL` persistence.

```bash
# 1. Navigate to project root
cd /path/to/java-project-main

# 2. Build the Linux Java 21 image and start SentinelCV in the background
docker compose up -d --build

# 3. View startup logs
docker compose logs -f sentinelcv

# 4. Stop without removing persisted datasets, models, DB, or keys
docker compose stop        # resume later with: docker compose start
# or remove container/network safely while keeping sentinelcv_data volume:
docker compose down        # resume later with: docker compose up -d
```

> **CRITICAL STORAGE WARNING**
> - **Never** bind-mount your macOS `./data` folder into `/app/data` (VirtioFS bind mounts do not provide the Linux `ext4` `SecureDirectoryStream` guarantees needed for dataset uploads).
> - **Never** run `docker compose down -v` unless you intentionally want to permanently erase `sentinelcv_data` (SQLite database, extracted datasets, uploaded models, reports, and `/app/data/keys/provenance.key`).

#### Running the Linux Test Suite in Docker
```bash
docker compose --profile test run --rm test
```

#### Backing Up and Restoring the `sentinelcv_data` Named Volume
```bash
# Backup /app/data from sentinelcv_data into a gzip archive in the current directory
docker run --rm --entrypoint sh --user 0 -v sentinelcv_data:/data:ro -v "$(pwd)":/backup sentinelcv:local \
  -c 'tar czf /backup/sentinelcv_data_backup.tar.gz -C /data .'

# Restore into sentinelcv_data (stop sentinelcv first with `docker compose stop`)
docker run --rm --entrypoint sh --user 0 -v sentinelcv_data:/data -v "$(pwd)":/backup sentinelcv:local \
  -c 'tar xzf /backup/sentinelcv_data_backup.tar.gz -C /data && chown -R 10001:10001 /data'
```

### 1.2 Native macOS / Linux (Development Only)
To run SentinelCV with the bundled Amazon Corretto 21 JDK and Apache Maven 3.9.6:
```bash
# 1. Navigate to project root
cd /path/to/Java-project

# 2. Make the run script executable
chmod +x run.sh

# 3. Start the application
./run.sh
```

### 1.3 Windows (Command Prompt / PowerShell / WSL)
If running under Windows WSL (Ubuntu / Debian):
```bash
./run.sh
```
If running natively on Windows with Java 21 and Maven installed:
```cmd
mvn spring-boot:run
```

The application will start on:
👉 **`http://localhost:8080`**

---

## 2. Default Configuration & Environment Variables
Configuration settings in `src/main/resources/application.properties`:

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `server.port` | `8080` | Web dashboard HTTP port |
| `sentinelcv.data.dir` | `./data` | Base directory for SQLite DB and file storage |
| `sentinelcv.upload.dir`| `./data/uploads` | Temporary upload directory |
| `sentinelcv.reports.dir`| `./data/reports` | Directory where PDF, HTML, and JSON reports are saved |
| `sentinelcv.datasets.dir`| `./data/datasets` | Directory where extracted datasets reside |
| `sentinelcv.models.dir` | `./data/models` | Directory where uploaded models reside |
| `sentinelcv.audit.thread-pool-size` | `4` | Max concurrency for background audit tasks |

To run on a different port or custom data directory:
```bash
./mvnw_local spring-boot:run -Dspring-boot.run.arguments="--server.port=9090 --sentinelcv.data.dir=/var/sentinelcv"
```

---

## 3. Recommended Faculty Demonstration Walkthrough

Follow this step-by-step sequence to demonstrate the entire application:

1. **Start Application**: Run `./run.sh` and open `http://localhost:8080` in your web browser.
2. **Dashboard Overview**: Show real SQLite-backed metrics (Datasets, ML Models, Inferences, Risk Score, Recent Audit History).
3. **Dataset Auditing**:
   - Navigate to **Datasets** (`/datasets`).
   - Notice pre-seeded `COCO Benchmark Sample` and `YOLO Traffic Sample`.
   - Click `Run Audit` on the COCO dataset.
   - Point out real detected findings: Exact Duplicate detected (sharing identical SHA-256), Contributor provenance metadata, Class distribution.
4. **Model Auditing**:
   - Navigate to **ML Models** (`/models`).
   - View `Demo Vision Classifier`.
   - Click `Run Audit`.
   - Show the computed SHA-256 fingerprint, structural behavioral fingerprint, and deterministic repeatability check.
5. **Inference Provenance**:
   - Navigate to **Provenance** (`/inference`).
   - Show the 3 cryptographically linked inference records.
   - Click `Verify Hash Chain` to demonstrate green `VERIFIED` status badges.
   - Click `Import Records` and paste a tampered JSON record to demonstrate broken hash chain or replay detection.
6. **Distribution Shift**:
   - Navigate to **Distribution Shift** (`/drift`).
   - Select Reference Dataset (`COCO Benchmark Sample`) and Evaluation Dataset (`YOLO Traffic Sample`).
   - Click `Analyze Shift`.
   - Show calculated Population Stability Index (PSI), Kolmogorov-Smirnov statistics, and visual feature breakdowns.
7. **Consolidated Security Findings**:
   - Navigate to **Security Findings** (`/findings`).
   - Filter by module (Dataset, Model, Inference, Drift) and severity (Critical, High, Medium, Low).
8. **Report Generation**:
   - Navigate to **Reports** (`/reports`).
   - Click `Generate New Report` and select `PDF`.
   - Download and open the generated PDF report showing executive summary, tables, and findings.
