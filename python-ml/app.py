"""
Phase 3 + 6 — Python ML Service
================================
Isolation Forest anomaly detection model served via Flask REST API.

Endpoints
---------
POST /predict        — classify a single log entry
POST /predict/batch  — classify a batch of log entries
GET  /health         — service health check
GET  /model/info     — model metadata
POST /model/retrain  — retrain model on fresh data
"""

import os
import json
import logging
import pickle
from datetime import datetime
from pathlib import Path

import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import LabelEncoder

# ── Logging ────────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s"
)
logger = logging.getLogger("ml-service")

# ── App ────────────────────────────────────────────────────────────────────────
app = Flask(__name__)
CORS(app)

MODEL_PATH = Path(__file__).parent / "models" / "isolation_forest.pkl"
MODEL_PATH.parent.mkdir(exist_ok=True)

# ── Feature Engineering ────────────────────────────────────────────────────────
LEVEL_ORDER = {"DEBUG": 0, "INFO": 1, "WARN": 2, "WARNING": 2, "ERROR": 3, "FATAL": 4}

ERROR_KEYWORDS = [
    "exception", "error", "fail", "timeout", "refused", "unavailable",
    "crash", "abort", "panic", "null", "undefined", "corrupt",
    "outofmemory", "heap space", "stack overflow", "deadlock"
]

WARNING_KEYWORDS = [
    "warn", "slow", "retry", "high", "limit", "threshold", "deprecated",
    "attempt", "unauthorized", "forbidden"
]


def extract_features(log: dict) -> list[float]:
    """
    Convert a log entry dict into a numeric feature vector.

    Features
    --------
    0  level_score       : numeric severity (0=DEBUG … 4=FATAL)
    1  msg_length        : character count of message
    2  error_kw_count    : number of error-related keywords in message
    3  warning_kw_count  : number of warning-related keywords
    4  has_stacktrace     : 1 if message contains newlines / "at com."
    5  hour_of_day       : 0-23 (night activity = unusual)
    6  is_weekend        : 1 if Saturday/Sunday
    """
    message = (log.get("message") or "").lower()
    level   = (log.get("level")   or "INFO").upper()
    ts_str  = log.get("timestamp") or datetime.now().isoformat()

    try:
        ts = datetime.fromisoformat(str(ts_str).replace("Z", ""))
    except Exception:
        ts = datetime.now()

    level_score     = float(LEVEL_ORDER.get(level, 1))
    msg_length      = float(len(message))
    error_kw        = float(sum(1 for kw in ERROR_KEYWORDS  if kw in message))
    warn_kw         = float(sum(1 for kw in WARNING_KEYWORDS if kw in message))
    has_stacktrace  = float(1 if ("at com." in message or "\n" in message) else 0)
    hour            = float(ts.hour)
    is_weekend      = float(1 if ts.weekday() >= 5 else 0)

    return [level_score, msg_length, error_kw, warn_kw, has_stacktrace, hour, is_weekend]


# ── Model Management ────────────────────────────────────────────────────────────

def build_default_model() -> IsolationForest:
    """Create and train a fresh Isolation Forest on synthetic normal logs."""
    logger.info("Training Isolation Forest on synthetic normal data …")

    rng = np.random.default_rng(42)
    n   = 2000

    # Simulate INFO / DEBUG logs (normal)
    normal = np.column_stack([
        rng.choice([0, 1], n),                  # level: DEBUG or INFO
        rng.integers(20, 200, n).astype(float),  # msg length
        np.zeros(n),                             # no error keywords
        rng.integers(0, 2, n).astype(float),     # 0-1 warn keywords
        np.zeros(n),                             # no stacktrace
        rng.integers(8, 18, n).astype(float),    # business hours
        np.zeros(n),                             # weekdays
    ])

    model = IsolationForest(
        n_estimators=200,
        contamination=0.05,   # expect ~5 % anomalies in production
        max_samples="auto",
        random_state=42,
    )
    model.fit(normal)

    # Persist
    with open(MODEL_PATH, "wb") as f:
        pickle.dump(model, f)
    logger.info("Model saved to %s", MODEL_PATH)
    return model


def load_model() -> IsolationForest:
    if MODEL_PATH.exists():
        logger.info("Loading persisted model from %s", MODEL_PATH)
        with open(MODEL_PATH, "rb") as f:
            return pickle.load(f)
    return build_default_model()


# ── Global Model Instance ──────────────────────────────────────────────────────
model: IsolationForest = load_model()
model_trained_at: str = datetime.now().isoformat()


# ── Routes ─────────────────────────────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "UP", "service": "log-anomaly-ml", "model_loaded": model is not None})


@app.route("/model/info", methods=["GET"])
def model_info():
    return jsonify({
        "algorithm": "IsolationForest",
        "n_estimators": model.n_estimators,
        "contamination": model.contamination,
        "trained_at": model_trained_at,
        "features": ["level_score", "msg_length", "error_kw_count",
                     "warn_kw_count", "has_stacktrace", "hour_of_day", "is_weekend"],
    })


@app.route("/predict", methods=["POST"])
def predict():
    """
    Classify a single log entry.

    Request  JSON: { logId, level, message, source, timestamp }
    Response JSON: { logId, anomaly, score, reason }
    """
    data = request.get_json(force=True)
    if not data:
        return jsonify({"error": "Empty request body"}), 400

    features = extract_features(data)
    X = np.array(features).reshape(1, -1)

    # IsolationForest: -1 = anomaly, +1 = normal
    raw_score = float(model.decision_function(X)[0])   # larger = more normal
    prediction = int(model.predict(X)[0])

    # Normalise score to [0, 1] where 1 = most anomalous
    anomaly_score = max(0.0, min(1.0, (0.5 - raw_score)))

    is_anomaly = (prediction == -1)
    reason     = _build_reason(data, features, is_anomaly)

    log_id = data.get("logId")
    logger.info("Prediction: logId=%s anomaly=%s score=%.4f", log_id, is_anomaly, anomaly_score)

    return jsonify({
        "logId":   log_id,
        "anomaly": is_anomaly,
        "score":   round(anomaly_score, 4),
        "reason":  reason,
    })


@app.route("/predict/batch", methods=["POST"])
def predict_batch():
    """Classify a list of log entries in one call."""
    data = request.get_json(force=True)
    if not isinstance(data, list):
        return jsonify({"error": "Expected a JSON array"}), 400

    results = []
    for entry in data:
        features = extract_features(entry)
        X = np.array(features).reshape(1, -1)
        raw_score     = float(model.decision_function(X)[0])
        prediction    = int(model.predict(X)[0])
        anomaly_score = max(0.0, min(1.0, (0.5 - raw_score)))
        is_anomaly    = (prediction == -1)
        results.append({
            "logId":   entry.get("logId"),
            "anomaly": is_anomaly,
            "score":   round(anomaly_score, 4),
            "reason":  _build_reason(entry, features, is_anomaly),
        })
    return jsonify(results)


@app.route("/model/retrain", methods=["POST"])
def retrain():
    """
    Retrain the model on provided normal log samples.
    Request JSON: { "logs": [ { level, message, timestamp, … }, … ] }
    """
    global model, model_trained_at

    data = request.get_json(force=True)
    logs = data.get("logs", [])

    if len(logs) < 50:
        return jsonify({"error": "Need at least 50 log samples to retrain"}), 400

    X = np.array([extract_features(l) for l in logs])
    new_model = IsolationForest(
        n_estimators=200,
        contamination=0.05,
        max_samples="auto",
        random_state=42,
    )
    new_model.fit(X)

    with open(MODEL_PATH, "wb") as f:
        pickle.dump(new_model, f)

    model           = new_model
    model_trained_at = datetime.now().isoformat()
    logger.info("Model retrained on %d samples", len(logs))

    return jsonify({"message": f"Model retrained on {len(logs)} samples", "trained_at": model_trained_at})


# ── Helper ─────────────────────────────────────────────────────────────────────

def _build_reason(log: dict, features: list, is_anomaly: bool) -> str:
    if not is_anomaly:
        return "normal"

    level   = (log.get("level") or "INFO").upper()
    message = (log.get("message") or "").lower()
    reasons = []

    if features[0] >= 3:   # ERROR/FATAL
        reasons.append(f"{level} log level")
    if features[2] > 0:
        reasons.append("error keywords in message")
    if features[4]:
        reasons.append("stack trace detected")
    if features[5] < 6 or features[5] > 22:
        reasons.append("unusual hour of activity")
    if features[6]:
        reasons.append("weekend activity")

    return "; ".join(reasons) if reasons else "statistical outlier"


# ── Entry Point ────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    logger.info("Starting ML service on port %d", port)
    app.run(host="0.0.0.0", port=port, debug=True)
