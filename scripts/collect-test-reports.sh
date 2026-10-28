#!/usr/bin/env bash
# Collect coverage reports into doc/test-reports/ for the submission package.
#
# Reads whatever the last test run produced; it does not run the tests itself, so the
# numbers in the summary are always traceable to a run you can repeat:
#
#   mvn -B verify                       # Java, needs Docker for Testcontainers
#   pnpm -r test                        # Node packages and the web app
#   cd services/ai-assistant-service && uv run pytest --cov
#
# See doc/04-m2-pricing-zones-issue-15.md for the Colima environment variables
# Testcontainers needs on this machine.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
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

# ---------------------------------------------------------------- Java (JaCoCo)
for csv in $(find . -path ./node_modules -prune -o -name jacoco.csv -print 2>/dev/null | sort); do
  module_dir="${csv%/target/site/jacoco/jacoco.csv}"
  module="$(basename "${module_dir}")"
  dest="${out}/java/${module}"
  mkdir -p "${dest}"
  cp -R "$(dirname "${csv}")/." "${dest}/"
  read -r lines branches <<<"$(python3 -I -c "
import csv, sys
rows = list(csv.DictReader(open(sys.argv[1])))
def ratio(miss, cov):
    m = sum(int(r[miss]) for r in rows)
    c = sum(int(r[cov]) for r in rows)
    return f'{c / (m + c) * 100:.2f}' if (m + c) else 'n/a'
print(ratio('LINE_MISSED', 'LINE_COVERED'), ratio('BRANCH_MISSED', 'BRANCH_COVERED'))
" "${csv}")"
  echo "| ${module} (Java) | ${lines}% | ${branches}% | \`${dest}/index.html\` |" >> "${summary}"
  found=$((found + 1))
done

# ---------------------------------------------------------------- Node and web (lcov)
# lcov.info is what the "lcov" reporter configured in apps/web/vitest.config.ts writes.
for lcov in $(find . -path ./node_modules -prune -o -name lcov.info -print 2>/dev/null | sort); do
  cov_dir="$(dirname "${lcov}")"
  module="$(basename "$(dirname "${cov_dir}")")"
  dest="${out}/node/${module}"
  mkdir -p "${dest}"
  cp -R "${cov_dir}/." "${dest}/"
  read -r lines branches <<<"$(python3 -I -c "
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
  echo "| ${module} (Node/web) | ${lines}% | ${branches}% | \`${dest}/lcov-report/index.html\` |" >> "${summary}"
  found=$((found + 1))
done

# ---------------------------------------------------------------- Python (coverage.xml)
for xml in $(find . -path ./node_modules -prune -o -name coverage.xml -print 2>/dev/null | sort); do
  # Only pytest-cov writes this name; vitest writes cobertura-coverage.xml and is handled above.
  module="$(basename "$(dirname "${xml}")")"
  dest="${out}/python/${module}"
  mkdir -p "${dest}"
  cp "${xml}" "${dest}/"
  read -r lines branches <<<"$(python3 -I -c "
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
line = float(root.get('line-rate', 0)) * 100
branch = float(root.get('branch-rate', 0)) * 100
print(f'{line:.2f}', f'{branch:.2f}')
" "${xml}")"
  echo "| ${module} (Python) | ${lines}% | ${branches}% | \`${dest}/coverage.xml\` |" >> "${summary}"
  found=$((found + 1))
done

if [ "${found}" -eq 0 ]; then
  echo "No coverage reports found. Run the test suites first." >&2
  exit 1
fi

{
  echo
  echo "Project gate: 70% lines per module (jacoco.minimum.line.coverage in"
  echo "libs/java/swiftdrop-parent/pom.xml, and the thresholds in apps/web/vitest.config.ts)."
} >> "${summary}"

echo "Collected ${found} report(s) into ${out}/"
cat "${summary}"
