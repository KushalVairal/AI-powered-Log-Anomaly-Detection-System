# 🔍 AI-Powered Log Anomaly Detection System

A full-stack system that ingests application logs, processes them through a Java Spring Boot backend,
runs anomaly detection using a Python Isolation Forest ML model, and visualises results in a live dashboard.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA |
| Database | H2 (dev) / MySQL 8 (prod) |
| ML Service | Python 3.10, Flask, scikit-learn (Isolation Forest) |
| Frontend | Vanilla HTML/CSS/JS Dashboard |
| Alerting | Spring Mail + console logging |

## Quick Start

```bash
# 1. Start Python ML service
cd python-ml && pip install -r requirements.txt && python app.py

# 2. Start Java backend (new terminal)
cd java-backend && mvn spring-boot:run

# 3. Open dashboard
open frontend/index.html

# 4. Generate test logs
cd python-ml && python log_generator.py --mode all
```

## Documentation

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for:
- Full system architecture diagram
- API reference
- ML model details
- Testing scenarios
- Production setup guide
