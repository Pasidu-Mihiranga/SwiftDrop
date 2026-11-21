#!/usr/bin/env bash
# Collect coverage reports into doc/test-reports/ for the submission package.
#
# Reads whatever the last test run produced; it does not run the tests itself, so the
# numbers in the summary are always traceable to a run you can repeat:
#
#   mvn -B verify                       # Java, needs Docker for Testcontainers
#   pnpm -r test                        # Node packages and the web app
#   cd services/ai-assistant-service && uv run pytest --cov
#   pnpm e2e                            # optional: Playwright demo suite, needs pnpm stack:up
#
# See doc/04-m2-pricing-zones-issue-15.md for the Colima environment variables
# Testcontainers needs on this machine.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# python3 where it exists; on Windows it can be the Microsoft Store placeholder, so fall back to python.
PY=python3
"${PY}" -c "" >/dev/null 2>&1 || PY=python
cd "${repo_root}"
out="doc/test-reports"
mkdir -p "${out}"

summary="${out}/SUMMARY.md"
: > "${summary}"
{
  echo "# Coverage summary"
  echo
  echo "Collected $(date -u +"%Y-%m-%d %H:%M UTC") by scripts/collect-test-reports.sh from the"
  echo "most recent local test run. Regenerate by re-running the tests and this script."
  echo
  echo "| Module | Lines | Branches | Report |"
  echo "|---|---|---|---|"
} >> "${summary}"

found=0
# "87.50" -> "87.50%"; a figure that was not measured stays "n/a".
pct() { if [ "$1" = "n/a" ]; then echo "n/a"; else echo "$1%"; fi; }

# ---------------------------------------------------------------- Java (JaCoCo)
for csv in $(find . \( -name node_modules -o -name .venv -o -path "./${out}" \) -prune -o -name jacoco.csv -print 2>/dev/null | sort); do
  module_dir="${csv%/target/site/jacoco/jacoco.csv}"
  module="$(basename "${module_dir}")"
  dest="${out}/java/${module}"
  mkdir -p "${dest}"
  cp -R "$(dirname "${csv}")/." "${dest}/"
  read -r lines branches <<<"$("${PY}" -I -c "
import csv, sys
rows = list(csv.DictReader(open(sys.argv[1])))
def ratio(miss, cov):
    m = sum(int(r[miss]) for r in rows)
    c = sum(int(r[cov]) for r in rows)
    return f'{c / (m + c) * 100:.2f}' if (m + c) else 'n/a'
print(ratio('LINE_MISSED', 'LINE_COVERED'), ratio('BRANCH_MISSED', 'BRANCH_COVERED'))
" "${csv}")"
  echo "| ${module} (Java) | $(pct "${lines}") | $(pct "${branches}") | \`${dest}/index.html\` |" >> "${summary}"
  found=$((found + 1))
done

# ---------------------------------------------------------------- Node and web (lcov)
# lcov.info is what the "lcov" reporter configured in apps/web/vitest.config.ts writes.
for lcov in $(find . \( -name node_modules -o -name .venv -o -path "./${out}" \) -prune -o -name lcov.info -print 2>/dev/null | sort); do
  cov_dir="$(dirname "${lcov}")"
  module="$(basename "$(dirname "${cov_dir}")")"
  dest="${out}/node/${module}"
  mkdir -p "${dest}"
  cp -R "${cov_dir}/." "${dest}/"
  read -r lines branches <<<"$("${PY}" -I -c "
import sys
lines_total = lines_hit = br_total = br_hit = 0
for raw in open(sys.argv[1]):
    raw = raw.strip()
    if raw.startswith('DA:'):
        lines_total += 1
        if int(raw[3:].split(',')[1]) > 0:
            lines_hit += 1
    elif raw.startswith('BRDA:'):
        taken = raw[5:].split(',')[3]
        br_total += 1
        if taken not in ('-', '0'):
            br_hit += 1
def ratio(hit, total):
    return f'{hit / total * 100:.2f}' if total else 'n/a'
print(ratio(lines_hit, lines_total), ratio(br_hit, br_total))
" "${lcov}")"
  echo "| ${module} (Node/web) | $(pct "${lines}") | $(pct "${branches}") | \`${dest}/lcov-report/index.html\` |" >> "${summary}"
  found=$((found + 1))
done

# ---------------------------------------------------------------- Python (coverage.xml)
for xml in $(find . \( -name node_modules -o -name .venv -o -path "./${out}" \) -prune -o -name coverage.xml -print 2>/dev/null | sort); do
  # Only pytest-cov writes this name; vitest writes cobertura-coverage.xml and is handled above.
  module="$(basename "$(dirname "${xml}")")"
  dest="${out}/python/${module}"
  mkdir -p "${dest}"
  cp "${xml}" "${dest}/"
  read -r lines branches <<<"$("${PY}" -I -c "
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
line = float(root.get('line-rate', 0)) * 100
# branches-valid is 0 when branch coverage was not measured: say so rather than report 0%.
measured = int(root.get('branches-valid', 0)) > 0
branch = format(float(root.get('branch-rate', 0)) * 100, '.2f') if measured else 'n/a'
print(f'{line:.2f}', branch)
" "${xml}")"
  echo "| ${module} (Python) | $(pct "${lines}") | $(pct "${branches}") | \`${dest}/coverage.xml\` |" >> "${summary}"
  found=$((found + 1))
done

# ---------------------------------------------------------------- End-to-end (Playwright)
# The suite writes straight into doc/test-reports/e2e/ (see tests/e2e/playwright.config.ts).
e2e_json="${out}/e2e/results.json"
e2e_summary=""
if [ -f "${e2e_json}" ]; then
  e2e_summary="$("${PY}" -I scripts/e2e-summary.py "${e2e_json}" "${out}")"
  found=$((found + 1))
fi

if [ "${found}" -eq 0 ]; then
  echo "No coverage reports found. Run the test suites first." >&2
  exit 1
fi

{
  echo
  echo "Project gate: 70% lines per module (jacoco.minimum.line.coverage in"
  echo "libs/java/swiftdrop-parent/pom.xml, and the thresholds in apps/web/vitest.config.ts)."
  if [ -n "${e2e_summary}" ]; then
    echo
    echo "## End-to-end demo suite (Playwright)"
    echo
    echo "${e2e_summary}"
  fi
} >> "${summary}"

echo "Collected ${found} report(s) into ${out}/"
cat "${summary}"
