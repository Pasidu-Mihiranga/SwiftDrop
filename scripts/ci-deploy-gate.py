#!/usr/bin/env python3
"""Decide whether the CI runs for one commit allow a deploy.

Reads `gh run list --json` on stdin. Prints two lines: a status word
(waiting, failed, or success) and the comma-separated ids of the CI runs.
The deploy workflow ignores its own run. A run that has not been created yet
is waiting, not a failure.
"""

import json
import sys

DEPLOY_WORKFLOW = "CD · deploy changed services"
FAILED = {"failure", "cancelled", "timed_out", "startup_failure"}


def ci_runs(runs):
    return [run for run in runs if run.get("event") == "push" and run.get("name") != DEPLOY_WORKFLOW]


def decide(runs):
    selected = ci_runs(runs)
    ids = ",".join(sorted(str(run.get("databaseId", run.get("name"))) for run in selected))
    if not selected:
        return "waiting", ids
    if any(run.get("conclusion") in FAILED for run in selected):
        return "failed", ids
    if any(run.get("status") != "completed" for run in selected):
        return "waiting", ids
    return "success", ids


def main():
    status, ids = decide(json.load(sys.stdin))
    print(status)
    print(ids)


if __name__ == "__main__":
    main()
