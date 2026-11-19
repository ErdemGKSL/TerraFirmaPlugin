#!/usr/bin/env python3
# Licensed under the EUPL, Version 1.2. PAR-REL-02: compare JMH results with budgets.json.
# Usage: check-budgets.py budgets.json results.json. Exit 1 when a result exceeds budget * (1 + tolerance).
# Set TFP_PERF_NOTE=1 (CI sets it when the PR body has a "perf-note:" line) to report but not fail.
import json, os, sys

cfg = json.load(open(sys.argv[1]))
results = {r["benchmark"]: r["primaryMetric"] for r in json.load(open(sys.argv[2]))}
tol = cfg["tolerance"]
failed = []
for name, b in cfg["budgets"].items():
    m = results.get(name)
    if m is None:
        failed.append(f"{b['id']} {name}: no result")
        continue
    if m["scoreUnit"] != b["unit"]:
        failed.append(f"{b['id']} {name}: unit {m['scoreUnit']} != {b['unit']}")
        continue
    limit = b["budget"] * (1 + tol)
    status = "ok" if m["score"] <= limit else "OVER"
    print(f"{status:4} {b['id']} {name}: {m['score']:.3f} {b['unit']} (budget {b['budget']}, limit {limit:.3f})")
    if status == "OVER":
        failed.append(f"{b['id']} {name}: {m['score']:.3f} > {limit:.3f} {b['unit']}")
if failed:
    print("\n".join("FAIL " + f for f in failed))
    if os.environ.get("TFP_PERF_NOTE") == "1":
        print("perf-note present: not failing the build")
        sys.exit(0)
    sys.exit(1)
