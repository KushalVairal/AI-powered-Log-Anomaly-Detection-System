# AI-Powered Log Anomaly Detection System
### Architecture, Flow & Setup Guide

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        APPLICATION LAYER                             │
│  Spring Boot App  →  Generates logs  →  logs/app.log               │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Log Ingest API    │
                    │  POST /api/logs/    │
                    │  ingest | ingest-raw│
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Preprocessing Svc  │
                    │  - Parse raw logs   │
                    │  - Extract fields   │
                    │  - Normalize levels │
                    └──────────┬──────────┘
                               │
               ┌───────────────▼───────────────┐
               │         MySQL / H2 DB          │
               │   log_entries | users tables   │
               └───────────────┬───────────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Java → Python call  │
                    │  RestTemplate        │
                    │  POST /predict       │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Python Flask ML    │
                    │  Isolation Forest   │
                    │  Feature extraction │
                    │  Returns score+flag │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Anomaly Decision  │
                    │   score > threshold │
                    │   → mark in DB      │
                    │   → trigger alert   │
                    └──────────┬──────────┘
                               │
               ┌───────────────┴───────────────┐
               │                               │
    ┌──────────▼──────────┐       ┌────────────▼──────────┐
    │    Alert Service    │       │  Dashboard API          │
    │  - Console warn     │       │  GET /api/logs/stats    │
    │  - Email (optional) │       │  GET /api/logs/anomalies│
    └─────────────────────┘       └────────────┬───────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  React Dashboard    │
                                    │  frontend/index.html│
                                    └─────────────────────┘
```

---

## Data Flow

```
Application → Logs → Preprocessing → Python ML Model → Anomaly Detection → Alerts → Dashboard
```

### Step-by-step:

1. **Log Generated** — Spring Boot app logs to console/file
2. **Ingestion** — Log sent to `POST /api/logs/ingest` (structured) or `/ingest-raw` (raw line)
3. **Preprocessing** — `LogPreprocessingService` parses and cleans the raw log
4. **DB Storage** — `LogEntry` saved to database
5. **ML Prediction** — `PythonMLClientService` sends log to Python `/predict` endpoint
6. **Anomaly Marking** — If `anomaly=true`, the log is flagged and score stored in DB
7. **Alert** — `AlertService` sends console warn + optional email
8. **Dashboard** — Frontend polls `/api/logs/stats` every 30 seconds

---

## Project Structure

```
log-anomaly-system/
├── java-backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/loganalyzer/
│       │   ├── LogAnomalyDetectorApplication.java   ← Entry point
│       │   ├── model/
│       │   │   ├── LogEntry.java                    ← Log entity
│       │   │   └── User.java                        ← Auth entity
│       │   ├── repository/
│       │   │   ├── LogEntryRepository.java
│       │   │   └── UserRepository.java
│       │   ├── dto/
│       │   │   └── Dtos.java                        ← All request/response DTOs
│       │   ├── service/
│       │   │   ├── LogPreprocessingService.java     ← Phase 2: Parse logs
│       │   │   ├── PythonMLClientService.java       ← Phase 4: Call Python ML
│       │   │   ├── LogAnalysisService.java          ← Core orchestrator
│       │   │   └── AlertService.java                ← Phase 5: Alerts
│       │   ├── controller/
│       │   │   ├── AuthController.java              ← Login/register API
│       │   │   └── LogController.java               ← Log ingestion + query
│       │   └── config/
│       │       ├── AppConfig.java                   ← Security, RestTemplate
│       │       └── DataInitializer.java             ← Demo data seeder
│       └── resources/
│           └── application.properties
├── python-ml/
│   ├── app.py                                       ← Flask ML service
│   ├── requirements.txt
│   ├── log_generator.py                            ← Test log generator
│   └── models/                                     ← Saved model files
├── frontend/
│   └── index.html                                  ← Dashboard UI
└── docs/
    └── ARCHITECTURE.md                             ← This file
```

---

## API Reference

### Java Backend (port 8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Authenticate user |
| POST | `/api/auth/register` | Register new user |
| POST | `/api/logs/ingest` | Ingest structured log |
| POST | `/api/logs/ingest-raw` | Ingest raw log line |
| GET  | `/api/logs` | Get latest 20 logs |
| GET  | `/api/logs/anomalies` | Get all anomalies |
| GET  | `/api/logs/stats` | Dashboard statistics |
| GET  | `/api/logs/demo/health` | Health check demo |
| GET  | `/api/logs/demo/error` | Trigger demo error log |
| GET  | `/h2-console` | H2 DB browser |

### Python ML Service (port 5000)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET  | `/health` | Service health |
| GET  | `/model/info` | Model metadata |
| POST | `/predict` | Classify single log |
| POST | `/predict/batch` | Classify batch of logs |
| POST | `/model/retrain` | Retrain model on new data |

---

## Setup Instructions

### Prerequisites
- Java 17+
- Maven 3.8+
- Python 3.10+
- MySQL 8 (or use embedded H2 for development)

### 1. Start Python ML Service

```bash
cd python-ml
pip install -r requirements.txt
python app.py
# → Running on http://localhost:5000
```

### 2. Start Java Backend

```bash
cd java-backend
mvn spring-boot:run
# → Running on http://localhost:8080
# → H2 Console: http://localhost:8080/h2-console
```

### 3. Open Dashboard

Open `frontend/index.html` in your browser.
Or serve with: `python -m http.server 3000` from the `frontend/` directory.

### 4. Generate Test Logs

```bash
cd python-ml

# Normal logs
python log_generator.py --mode normal --count 30

# Inject anomalies
python log_generator.py --mode error --count 15

# High traffic burst
python log_generator.py --mode burst --count 50

# Run all scenarios
python log_generator.py --mode all
```

### 5. Switch to MySQL (Production)

In `application.properties`, comment out H2 and uncomment MySQL:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/loganalyzer
spring.datasource.username=root
spring.datasource.password=yourpassword
```

Create DB:
```sql
CREATE DATABASE loganalyzer;
```

---

## ML Model Details

**Algorithm:** Isolation Forest (scikit-learn)

**Why Isolation Forest?**
- Unsupervised — no labelled anomaly data needed
- Trains on "normal" logs only
- Naturally handles high-dimensional feature spaces
- Fast prediction (O log n)

**Feature Vector (7 features):**

| # | Feature | Description |
|---|---------|-------------|
| 0 | `level_score` | Numeric severity: DEBUG=0, INFO=1, WARN=2, ERROR=3, FATAL=4 |
| 1 | `msg_length` | Character count of log message |
| 2 | `error_kw_count` | Count of error-related keywords (exception, timeout, etc.) |
| 3 | `warn_kw_count` | Count of warning-related keywords |
| 4 | `has_stacktrace` | 1 if message contains "at com." or newlines |
| 5 | `hour_of_day` | Hour 0–23 (unusual hours = potentially suspicious) |
| 6 | `is_weekend` | 1 if Saturday/Sunday |

**Fallback:** If the Python service is unreachable, Java falls back to rule-based detection (ERROR/FATAL levels and exception keywords).

---

## Demo Credentials

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | ADMIN |
| user | user123 | USER |

---

## Testing Scenarios (Phase 7)

| Scenario | Expected Result |
|----------|----------------|
| Normal INFO log | `anomaly: false`, score < 0.3 |
| ERROR with exception | `anomaly: true`, score > 0.8 |
| OutOfMemoryError | `anomaly: true`, score > 0.9 |
| WARN about high load | `anomaly: false`, score ~0.4 |
| Login failure burst | `anomaly: true`, score > 0.8 |
| Late-night ERROR | `anomaly: true` (unusual hour) |
