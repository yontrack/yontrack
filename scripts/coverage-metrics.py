#!/usr/bin/env python3
"""Computes the six sets of coverage figures from the JaCoCo XML reports (#1821).

The parsing half of scripts/coverage-metrics.sh, which is the entry point and the place the
contract is documented. XML parsing in bash is not sensible; everything else about the figures --
when they are computed, where they go -- belongs to the shell script, as it does for every other
CI-logic script in this repository.

Reads the XML rather than the HTML on purpose: a JaCoCo XML report carries, per source file,
``<line nr ci mi cb mb/>``, which is what ``line``, ``branch`` and ``unique_line`` are all defined
in terms of. A line is covered when ``ci > 0``.

Nothing here reads a $GITHUB_* variable.
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

# The four backend test types, and the order they are reported in.
TYPES = ("unit", "integration", "kdsl", "ui")


class MetricsError(Exception):
    """Something that makes a figure impossible. Never a partial answer."""


def read_report(path: Path) -> tuple[set[tuple[str, str, int]], set[tuple[str, str, int]], int, int]:
    """Reads one JaCoCo XML report.

    Returns the set of *all* lines, the set of *covered* lines -- both as
    ``(package, sourcefile, line number)`` triples, so they can be compared across reports -- and
    the covered and missed branch counts.
    """
    if not path.is_file():
        raise MetricsError(f"No JaCoCo XML report at {path}")
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        raise MetricsError(f"Could not parse {path}: {error}") from error

    all_lines: set[tuple[str, str, int]] = set()
    covered_lines: set[tuple[str, str, int]] = set()
    covered_branches = 0
    missed_branches = 0

    for package in root.iter("package"):
        package_name = package.get("name", "")
        for sourcefile in package.findall("sourcefile"):
            file_name = sourcefile.get("name", "")
            for line in sourcefile.findall("line"):
                number = int(line.get("nr", "0"))
                key = (package_name, file_name, number)
                all_lines.add(key)
                if int(line.get("ci", "0")) > 0:
                    covered_lines.add(key)
                covered_branches += int(line.get("cb", "0"))
                missed_branches += int(line.get("mb", "0"))

    return all_lines, covered_lines, covered_branches, missed_branches


def one_decimal(value: Decimal | float) -> float:
    """Rounds to one decimal, half *up*.

    Deliberately not Python's ``round``, which rounds half to even: 61.25 would come out as 61.2
    on one run and 61.35 as 61.4 on the next, and a coverage figure that a reader can reproduce
    with a calculator matters more here than statistical neutrality.
    """
    return float(Decimal(str(value)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP))


def percentage(covered: int, total: int) -> float:
    """A percentage from 0 to 100 with one decimal. An empty denominator is 0.0, not a crash:
    a report with no lines at all is already caught by the caller."""
    if total <= 0:
        return 0.0
    return one_decimal(Decimal(covered) * 100 / Decimal(total))


def jest_metrics(path: Path) -> dict[str, float]:
    """COVERAGE.UI_UNIT, from Jest's own summary (#1820) -- not from JaCoCo."""
    if not path.is_file():
        raise MetricsError(f"No Jest coverage summary at {path}")
    try:
        summary = json.loads(path.read_text(encoding="utf-8"))
        total = summary["total"]
        return {
            "line": one_decimal(float(total["lines"]["pct"])),
            "branch": one_decimal(float(total["branches"]["pct"])),
        }
    except (ValueError, KeyError, TypeError) as error:
        raise MetricsError(f"Could not read the Jest coverage summary {path}: {error}") from error


def compute(report_dir: Path, jest_summary: Path) -> dict[str, dict[str, float]]:
    reports = {name: read_report(report_dir / name / "jacoco.xml") for name in TYPES}
    merged_all, merged_covered, merged_cb, merged_mb = read_report(report_dir / "merged" / "jacoco.xml")

    if not merged_all:
        raise MetricsError(
            f"The merged report {report_dir / 'merged' / 'jacoco.xml'} has no lines at all; "
            "the class files the report was built from are wrong."
        )

    metrics: dict[str, dict[str, float]] = {}
    for name in TYPES:
        all_lines, covered, cb, mb = reports[name]
        # Every per-type report is built from the same class files as the merged one, so the
        # denominators must agree. When they do not, the exclusion list was applied inconsistently
        # and the figures are not comparable -- which is the one thing `unique_line` needs.
        if all_lines != merged_all:
            raise MetricsError(
                f"The '{name}' report covers {len(all_lines)} lines where the merged report covers "
                f"{len(merged_all)}: the reports were built from different class files."
            )
        others: set[tuple[str, str, int]] = set()
        for other in TYPES:
            if other != name:
                others |= reports[other][1]
        metrics[name] = {
            "line": percentage(len(covered), len(merged_all)),
            "branch": percentage(cb, cb + mb),
            "unique_line": percentage(len(covered - others), len(merged_all)),
        }

    metrics["total"] = {
        "line": percentage(len(merged_covered), len(merged_all)),
        "branch": percentage(merged_cb, merged_cb + merged_mb),
    }
    metrics["ui_unit"] = jest_metrics(jest_summary)
    return metrics


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report_dir", type=Path, help="directory holding <type>/jacoco.xml and merged/jacoco.xml")
    parser.add_argument("jest_summary", type=Path, help="ontrack-web-core/coverage/coverage-summary.json")
    parser.add_argument("--output", type=Path, help="where the JSON document is written (default: stdout)")
    args = parser.parse_args(argv)

    try:
        metrics = compute(args.report_dir, args.jest_summary)
    except MetricsError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    document = json.dumps(metrics, indent=2, sort_keys=True)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(document + "\n", encoding="utf-8")
    else:
        print(document)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
