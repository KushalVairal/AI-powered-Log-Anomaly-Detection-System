"""
Phase 7 – Test Log Generator
=============================
Generates realistic log streams to test the anomaly detection system.

Usage
-----
  python log_generator.py              # normal mix (default)
  python log_generator.py --mode error # inject errors/anomalies
  python log_generator.py --mode burst # high-volume burst
  python log_generator.py --mode all   # run all scenarios
"""

import argparse
import json
import random
import time
from datetime import datetime

import requests

API_URL = "http://localhost:8080/api/logs/ingest"

# ── Templates ──────────────────────────────────────────────────────────────────
NORMAL_LOGS = [
    ("INFO",  "UserService",      "User '{user}' logged in successfully"),
    ("INFO",  "OrderService",     "Order #{order_id} created for user '{user}'"),
    ("INFO",  "PaymentService",   "Payment of ${amount} processed for order #{order_id}"),
    ("DEBUG", "CacheService",     "Cache hit for key: product:{product_id}"),
    ("DEBUG", "SessionService",   "Session refreshed for user '{user}'"),
    ("INFO",  "InventoryService", "Stock level for SKU-{product_id}: {stock} units"),
    ("INFO",  "NotificationSvc",  "Email notification sent to {user}@example.com"),
    ("INFO",  "HealthCheck",      "System health check passed — all services UP"),
    ("INFO",  "ApiGateway",       "Request {method} /api/{endpoint} completed in {ms}ms"),
    ("WARN",  "DatabasePool",     "Connection pool utilization at {pct}%"),
]

ANOMALY_LOGS = [
    ("ERROR", "AuthService",      "Invalid JWT token — authentication failed for user '{user}'"),
    ("ERROR", "DatabaseService",  "Connection timeout after 30000ms — java.sql.SQLTimeoutException"),
    ("ERROR", "InventoryService", "OutOfMemoryError: Java heap space"),
    ("ERROR", "PaymentService",   "Payment gateway unreachable — ConnectionRefusedException"),
    ("ERROR", "UserService",      "NullPointerException at UserService.getById(UserService.java:142)"),
    ("WARN",  "SecurityFilter",   "{count} consecutive failed login attempts for IP {ip}"),
    ("ERROR", "OrderService",     "Transaction rollback: duplicate order #{order_id} detected"),
    ("WARN",  "ApiGateway",       "High response time: {ms}ms on POST /api/checkout (threshold: 2000ms)"),
    ("ERROR", "CacheService",     "Redis connection lost — falling back to database"),
    ("ERROR", "SchedulerService", "Critical job 'daily-reconciliation' failed: divide by zero"),
]

SERVICES = ["UserService", "OrderService", "PaymentService", "CacheService",
            "AuthService", "DatabaseService", "ApiGateway", "InventoryService"]

USERS   = ["alice", "bob", "charlie", "diana", "eve"]
METHODS = ["GET", "POST", "PUT", "DELETE"]
ENDPOINTS = ["users", "orders", "products", "checkout", "payments"]


def _fill(template: str) -> str:
    return template.format(
        user=random.choice(USERS),
        order_id=random.randint(1000, 9999),
        product_id=random.randint(1, 500),
        amount=round(random.uniform(10, 500), 2),
        stock=random.randint(0, 200),
        ms=random.randint(50, 4000),
        pct=random.randint(60, 95),
        method=random.choice(METHODS),
        endpoint=random.choice(ENDPOINTS),
        ip=f"192.168.{random.randint(1,10)}.{random.randint(1,254)}",
        count=random.randint(3, 10),
    )


def send_log(level: str, source: str, message: str) -> dict | None:
    payload = {
        "level":     level,
        "source":    source,
        "message":   message,
        "timestamp": datetime.now().isoformat(),
        "threadName": f"thread-{random.randint(1, 20)}",
        "requestId":  f"req-{random.randint(10000, 99999)}",
    }
    try:
        resp = requests.post(API_URL, json=payload, timeout=5)
        resp.raise_for_status()
        return resp.json()
    except Exception as ex:
        print(f"  [WARN] Could not send log: {ex}")
        return None


def scenario_normal(count: int = 30, delay: float = 0.3):
    """Generate a stream of normal logs."""
    print(f"\n📋 Normal scenario — {count} logs")
    for i in range(count):
        level, source, tmpl = random.choice(NORMAL_LOGS)
        result = send_log(level, source, _fill(tmpl))
        status = "✓" if result else "✗"
        print(f"  {status} [{level:<5}] {source}: {_fill(tmpl)[:60]}")
        time.sleep(delay)


def scenario_error(count: int = 15, delay: float = 0.5):
    """Inject anomalous/error logs."""
    print(f"\n🚨 Error scenario — {count} anomaly logs")
    for i in range(count):
        level, source, tmpl = random.choice(ANOMALY_LOGS)
        msg = _fill(tmpl)
        result = send_log(level, source, msg)
        status = "✓" if result else "✗"
        print(f"  {status} [{level:<5}] {source}: {msg[:60]}")
        time.sleep(delay)


def scenario_burst(count: int = 50, delay: float = 0.05):
    """High-volume burst test."""
    print(f"\n⚡ Burst scenario — {count} rapid logs")
    for i in range(count):
        if random.random() < 0.15:   # 15 % anomalies in burst
            level, source, tmpl = random.choice(ANOMALY_LOGS)
        else:
            level, source, tmpl = random.choice(NORMAL_LOGS)
        send_log(level, source, _fill(tmpl))
        print(f"  → {i+1}/{count}", end="\r")
        time.sleep(delay)
    print(f"\n  Done — {count} logs sent")


def scenario_mixed(normal: int = 20, errors: int = 5):
    """Realistic mixed stream."""
    print(f"\n🔀 Mixed scenario — {normal} normal + {errors} errors")
    pool = (
        [(False, *t) for t in NORMAL_LOGS] * normal +
        [(True,  *t) for t in ANOMALY_LOGS] * errors
    )
    random.shuffle(pool)
    for is_anom, level, source, tmpl in pool:
        msg = _fill(tmpl)
        result = send_log(level, source, msg)
        tag = "🚨" if is_anom else "  "
        print(f"  {tag} [{level:<5}] {source}: {msg[:55]}")
        time.sleep(0.2)


# ── CLI ────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Log Anomaly System — test log generator")
    parser.add_argument("--mode", choices=["normal", "error", "burst", "mixed", "all"],
                        default="mixed", help="Scenario to run")
    parser.add_argument("--count", type=int, default=30, help="Number of logs to generate")
    args = parser.parse_args()

    print(f"🔌 Sending logs to {API_URL}")
    print("─" * 60)

    if args.mode == "normal":
        scenario_normal(args.count)
    elif args.mode == "error":
        scenario_error(args.count)
    elif args.mode == "burst":
        scenario_burst(args.count)
    elif args.mode == "mixed":
        scenario_mixed()
    elif args.mode == "all":
        scenario_normal(20)
        scenario_error(10)
        scenario_burst(30)

    print("\n✅ Done!")
