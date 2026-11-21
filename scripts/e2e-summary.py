"""Summarises a Playwright JSON report as Markdown for doc/test-reports/SUMMARY.md.

usage: python3 scripts/e2e-summary.py doc/test-reports/e2e/results.json doc/test-reports
Called by scripts/collect-test-reports.sh.
"""

import json
import sys


def main(results_path: str, out_dir: str) -> None:
    with open(results_path, encoding="utf-8") as handle:
        report = json.load(handle)
    stats = report["stats"]
    skipped: list[str] = []

    def walk(suite: dict) -> None:
        for spec in suite.get("specs", []):
            for test in spec["tests"]:
                result = test["results"][-1] if test["results"] else {"status": "skipped"}
                if result["status"] != "skipped":
                    continue
                notes = test.get("annotations", []) + result.get("annotations", [])
                reason = next((n["description"] for n in notes if n.get("description")), "no reason given")
                skipped.append(f"- {spec['title']}: {reason}")
        for child in suite.get("suites", []):
            walk(child)

    for suite in report["suites"]:
        walk(suite)

    started = stats["startTime"][:16].replace("T", " ")
    print(f"Run {started} UTC: **{stats['expected']} passed**, {stats['unexpected']} failed, "
          f"{stats['flaky']} flaky, {stats['skipped']} skipped. HTML report: `{out_dir}/e2e/html/index.html`, "
          f"JUnit: `{out_dir}/e2e/junit.xml`.")
    if skipped:
        print()
        print("Skipped, with the reason the suite gave:")
        print()
        print("\n".join(skipped))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
