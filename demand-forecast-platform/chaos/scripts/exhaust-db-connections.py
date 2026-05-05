#!/usr/bin/env python3
"""
DB Connection Pool Exhaustion tool.
Opens N idle connections to a PostgreSQL database and holds them for a duration.
Use with chaos-toolkit db-exhaustion-experiment.json.

Usage:
    python3 exhaust-db-connections.py \
        --host localhost --port 5433 --db orderdb \
        --user orderuser --password secret \
        --connections 15 --hold 120
"""
import argparse
import signal
import sys
import time

import psycopg2

conns = []


def cleanup(signum=None, frame=None):
    print(f"[CHAOS/db-exhaust] Closing {len(conns)} connections...", flush=True)
    for c in conns:
        try:
            c.close()
        except Exception:
            pass
    print("[CHAOS/db-exhaust] All connections closed.", flush=True)
    sys.exit(0)


signal.signal(signal.SIGTERM, cleanup)
signal.signal(signal.SIGINT, cleanup)

parser = argparse.ArgumentParser()
parser.add_argument("--host", default="localhost")
parser.add_argument("--port", type=int, default=5433)
parser.add_argument("--db", default="orderdb")
parser.add_argument("--user", required=True)
parser.add_argument("--password", required=True)
parser.add_argument("--connections", type=int, default=15)
parser.add_argument("--hold", type=int, default=120)
args = parser.parse_args()

DSN = f"host={args.host} port={args.port} dbname={args.db} user={args.user} password={args.password} application_name=chaos-exhaust connect_timeout=5"

print(f"[CHAOS/db-exhaust] Opening {args.connections} connections to {args.host}:{args.port}/{args.db}", flush=True)

for i in range(args.connections):
    try:
        conn = psycopg2.connect(DSN)
        conn.autocommit = True
        # Run SELECT 1 to confirm the connection is live
        with conn.cursor() as cur:
            cur.execute("SELECT pg_backend_pid(), now()")
            pid, ts = cur.fetchone()
        conns.append(conn)
        print(f"[CHAOS/db-exhaust]   [{i+1:02d}/{args.connections}] Connected — backend PID {pid}", flush=True)
    except Exception as e:
        print(f"[CHAOS/db-exhaust]   [{i+1:02d}/{args.connections}] FAILED: {e}", flush=True)

print(f"[CHAOS/db-exhaust] Holding {len(conns)} connections for {args.hold}s...", flush=True)

# Report active connections every 10s
start = time.time()
while time.time() - start < args.hold:
    elapsed = int(time.time() - start)
    print(f"[CHAOS/db-exhaust] T+{elapsed}s | holding {len(conns)} connections", flush=True)
    time.sleep(10)

cleanup()
