#!/usr/bin/env python3
"""Bundles the JUnit reports of a stamp and renders its evidence as one HTML file (#2128).

The parsing and rendering half of scripts/ci-evidence.sh, which is the entry point and the place
the rules are written down. Standard library only.

Every value that reaches the HTML goes through ``e()``: test names and finding titles are
untrusted, and the file is opened locally. The page loads nothing, and its only link is the
workflow run, when that is an https URL.

Nothing here reads a $GITHUB_* variable.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from pathlib import Path

# As FindingSeverity orders them: the most severe first.
SEVERITIES = ("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")

STYLE = """
body { font: 14px/1.45 system-ui, sans-serif; margin: 2em auto; max-width: 75em; padding: 0 1em; color: #1a1a1a; }
h1 { font-size: 1.5em; } h2 { font-size: 1.15em; margin-top: 2em; }
table { border-collapse: collapse; width: 100%; margin: .5em 0; }
th, td { border: 1px solid #ccc; padding: .25em .5em; text-align: left; vertical-align: top; overflow-wrap: anywhere; }
th { background: #f2f2f2; }
dl { display: grid; grid-template-columns: max-content 1fr; gap: .15em 1em; }
dt { font-weight: 600; } dd { margin: 0; overflow-wrap: anywhere; }
pre { background: #f6f6f6; border: 1px solid #ddd; padding: .5em; overflow-x: auto; white-space: pre-wrap; overflow-wrap: anywhere; }
summary { cursor: pointer; margin: .25em 0; }
.failed, .critical, .high { color: #a40000; font-weight: 600; }
.skipped, .accepted { color: #595959; }
.medium { color: #8a4b00; font-weight: 600; }
"""


class EvidenceError(Exception):
    """Something that makes a report impossible. Never a partial file."""


def e(value: object) -> str:
    """Every value on its way to the HTML, whatever it is and wherever it lands."""
    return html.escape("" if value is None else str(value), quote=True)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_atomically(output: Path, write) -> None:
    """A file that failed half way must not be left behind: it would be passed as evidence."""
    output.parent.mkdir(parents=True, exist_ok=True)
    handle, name = tempfile.mkstemp(dir=output.parent, prefix=f".{output.name}.")
    os.close(handle)
    temporary = Path(name)
    try:
        write(temporary)
        os.chmod(temporary, 0o644)
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)


# ---------------------------------------------------------------------------------------------
# The files of a pattern
# ---------------------------------------------------------------------------------------------


def pattern_to_regex(pattern: str) -> re.Pattern[str]:
    """The pattern as the CLI reads it (gobwas/glob, with `/` as separator): `**` crosses
    directories, `*` and `?` do not. The zip has to hold the files the stamp counted, which rules
    out Python's own glob: its `**/` also matches nothing at all, and it skips hidden directories.
    """
    regex = ""
    position = 0
    while position < len(pattern):
        if pattern.startswith("**", position):
            regex += ".*"
            position += 2
        elif pattern[position] == "*":
            regex += "[^/]*"
            position += 1
        elif pattern[position] == "?":
            regex += "[^/]"
            position += 1
        else:
            regex += re.escape(pattern[position])
            position += 1
    return re.compile(regex)


def matching_files(pattern: str) -> list[Path]:
    regex = pattern_to_regex(pattern)
    found = []
    for directory, _, files in os.walk("."):
        for file in files:
            path = os.path.normpath(os.path.join(directory, file))
            if regex.fullmatch(path.replace(os.sep, "/")):
                found.append(Path(path))
    return sorted(found)


def bundle(pattern: str, output: Path) -> None:
    files = matching_files(pattern)
    if not files:
        raise EvidenceError(f"No file matches {pattern}")

    def write(temporary: Path) -> None:
        # strict_timestamps off: a file coming out of an artefact can carry any date.
        with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED, strict_timestamps=False) as archive:
            for file in files:
                archive.write(file, file.as_posix())

    write_atomically(output, write)


# ---------------------------------------------------------------------------------------------
# The page
# ---------------------------------------------------------------------------------------------


@dataclass
class Header:
    stamp: str
    build: str
    commit: str
    run_url: str
    raws: list[Path] = field(default_factory=list)


def run_link(url: str) -> str:
    if url.startswith("https://"):
        return f'<a href="{e(url)}" rel="noopener noreferrer">{e(url)}</a>'
    return e(url)


def page(header: Header, kind: str, body: str) -> str:
    inputs = "".join(f"<dt>SHA-256 of {e(raw.name)}</dt><dd><code>{e(sha256(raw))}</code></dd>" for raw in header.raws)
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
<title>{e(header.stamp)} - {e(kind)}</title>
<style>{STYLE}</style>
</head>
<body>
<h1>{e(header.stamp)}</h1>
<dl>
<dt>Stamp</dt><dd>{e(header.stamp)}</dd>
<dt>Build</dt><dd>{e(header.build)}</dd>
<dt>Commit</dt><dd><code>{e(header.commit)}</code></dd>
<dt>Workflow run</dt><dd>{run_link(header.run_url)}</dd>
<dt>Generated</dt><dd>{e(generated)}</dd>
{inputs}
</dl>
{body}
</body>
</html>
"""


def total(name: str, value: int) -> str:
    return f'<tr><th>{e(name)}</th><td data-total="{e(name.lower())}">{e(value)}</td></tr>'


# ---------------------------------------------------------------------------------------------
# JUnit
# ---------------------------------------------------------------------------------------------


@dataclass
class Test:
    name: str
    classname: str
    status: str
    time: str
    message: str
    trace: str


@dataclass
class Suite:
    name: str
    passed: int
    skipped: int
    failed: int
    tests: list[Test]


def attribute(suite: ET.Element, name: str, path: Path) -> int:
    try:
        return int(suite.get(name) or 0)
    except ValueError as error:
        raise EvidenceError(f"{path}: `{name}` is not a number") from error


def read_test(test: ET.Element) -> Test:
    problem = test.find("failure")
    if problem is None:
        problem = test.find("error")
    if problem is not None:
        status, message, trace = "failed", problem.get("message", ""), problem.text or ""
    elif test.find("skipped") is not None:
        status, message, trace = "skipped", test.find("skipped").get("message", ""), ""
    else:
        status, message, trace = "passed", "", ""
    return Test(test.get("name", ""), test.get("classname", ""), status, test.get("time", ""), message, trace)


def read_suites(path: Path) -> list[Suite]:
    """The counts are read off the suite headers, the way the CLI computes the stamp, so that the
    report and the stamp say the same thing: the children of the root when it has any, else the
    root. `system-out` and `system-err` are never read."""
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        raise EvidenceError(f"Could not parse {path}: {error}") from error
    suites = []
    for suite in root.findall("testsuite") or [root]:
        tests = attribute(suite, "tests", path)
        skipped = attribute(suite, "skipped", path)
        failures = attribute(suite, "failures", path)
        errors = attribute(suite, "errors", path)
        suites.append(
            Suite(
                name=suite.get("name") or path.name,
                passed=tests - skipped - failures - errors,
                skipped=skipped,
                failed=failures + errors,
                tests=[read_test(test) for test in suite.iter("testcase")],
            )
        )
    return suites


def render_suite(suite: Suite) -> str:
    rows = []
    failures = []
    for test in suite.tests:
        rows.append(
            f'<tr><td>{e(test.name)}</td><td>{e(test.classname)}</td><td class="{e(test.status)}">{e(test.status)}</td><td>{e(test.time)}</td></tr>'
        )
        if test.status == "failed":
            failures.append(f"<h3>{e(test.name)}</h3><p>{e(test.message)}</p><pre>{e(test.trace)}</pre>")
    opened = " open" if suite.failed else ""
    return (
        f"<details{opened}><summary>{e(suite.name)} ({e(len(suite.tests))})</summary>"
        "<table><tr><th>Test</th><th>Class</th><th>Status</th><th>Time (s)</th></tr>"
        f"{''.join(rows)}</table>{''.join(failures)}</details>"
    )


def render_junit(pattern: str, header: Header) -> str:
    files = matching_files(pattern)
    if not files:
        raise EvidenceError(f"No file matches {pattern}")
    suites = [suite for file in files for suite in read_suites(file)]
    summary = "".join(
        f'<tr><td>{e(suite.name)}</td><td>{e(suite.passed)}</td><td>{e(suite.skipped)}</td><td>{e(suite.failed)}</td></tr>'
        for suite in suites
    )
    body = (
        "<h2>Totals</h2><table>"
        + total("Passed", sum(suite.passed for suite in suites))
        + total("Skipped", sum(suite.skipped for suite in suites))
        + total("Failed", sum(suite.failed for suite in suites))
        + "</table><h2>Suites</h2><table><tr><th>Suite</th><th>Passed</th><th>Skipped</th><th>Failed</th></tr>"
        + summary
        + "</table><h2>Tests</h2>"
        + "".join(render_suite(suite) for suite in suites)
    )
    return page(header, "test report", body)


# ---------------------------------------------------------------------------------------------
# Findings
# ---------------------------------------------------------------------------------------------


@dataclass
class Finding:
    external_id: str
    location: str
    severity: str
    title: str
    installed: str = ""
    fixed: str = ""
    accepted: bool = False
    statement: str = ""
    source: str = ""


def severity_of(raw: object) -> str:
    text = str(raw or "").upper()
    return text if text in SEVERITIES else "UNKNOWN"


def normalise_location(location: str) -> str:
    """FindingLocations.normalise: a purl without its version and its qualifiers."""
    if not location.lower().startswith("pkg:"):
        return location
    rest, hash_sign, subpath = location.partition("#")
    rest = rest.partition("?")[0]
    name_index = rest.rfind("/") + 1
    version_index = rest.find("@", name_index)
    if version_index >= 0:
        rest = rest[:version_index]
    return rest + hash_sign + subpath


def rank(finding: Finding) -> tuple[bool, int]:
    """Not accepted before accepted, then the most severe."""
    return finding.accepted, SEVERITIES.index(finding.severity)


def consolidate(findings: list[Finding]) -> list[Finding]:
    """FindingsConsolidation.consolidate: the report and the stamp have to count alike. One
    external ID on one location is one finding: not accepted before accepted, then the most
    severe, then the first of the report."""
    kept: dict[tuple[str, str], Finding] = {}
    for finding in findings:
        key = (finding.external_id, finding.location)
        if key not in kept or rank(finding) < rank(kept[key]):
            kept[key] = finding
    return list(kept.values())


def read_json(path: Path) -> dict:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise EvidenceError(f"Could not read {path}: {error}") from error
    if not isinstance(document, dict):
        raise EvidenceError(f"{path} is not a JSON object")
    return document


def read_trivy_vulnerability(vulnerability: dict, accepted: bool, statement: str, source: str) -> Finding:
    """As TrivyFindingsReportParser reads one."""
    external_id = vulnerability.get("VulnerabilityID")
    location = (vulnerability.get("PkgIdentifier") or {}).get("PURL") or vulnerability.get("PkgName")
    if not external_id or not location:
        raise EvidenceError("A Trivy vulnerability has no ID or no package")
    return Finding(
        external_id=external_id,
        location=normalise_location(location),
        severity=severity_of(vulnerability.get("Severity")),
        title=vulnerability.get("Title") or external_id,
        installed=vulnerability.get("InstalledVersion") or "",
        fixed=vulnerability.get("FixedVersion") or "",
        accepted=accepted,
        statement=statement,
        source=source,
    )


def read_trivy(path: Path) -> list[Finding]:
    """The vulnerabilities only, as TrivyFindingsReportParser. Never the secrets, the
    misconfigurations or the licences of a result, and the type of a modified finding comes first:
    a modified secret carries the matched text."""
    findings = []
    for result in read_json(path).get("Results") or []:
        for vulnerability in result.get("Vulnerabilities") or []:
            findings.append(read_trivy_vulnerability(vulnerability, False, "", ""))
        for modified in result.get("ExperimentalModifiedFindings") or []:
            if modified.get("Type") == "vulnerability" and modified.get("Status") in ("ignored", "not_affected"):
                source = modified.get("Source") or f"Trivy ({modified.get('Status')})"
                findings.append(
                    read_trivy_vulnerability(modified.get("Finding") or {}, True, modified.get("Statement") or "", source)
                )
    return consolidate(findings)


def is_effective(acceptance: dict) -> bool:
    """FindingAcceptance.isEffectiveOn, on the day of the report: an expired one counts again."""
    expires = acceptance.get("expiresAt")
    return not expires or date.fromisoformat(expires) >= datetime.now(timezone.utc).date()


def read_findings(path: Path) -> list[Finding]:
    findings = []
    for entry in read_json(path).get("findings") or []:
        acceptance = entry.get("acceptance")
        findings.append(
            Finding(
                external_id=str(entry.get("externalId") or ""),
                location=normalise_location(str(entry.get("location") or "")),
                severity=severity_of(entry.get("severity")),
                title=str(entry.get("title") or ""),
                installed=str(entry.get("installedVersion") or ""),
                fixed=str(entry.get("fixedVersion") or ""),
                accepted=bool(acceptance) and is_effective(acceptance),
                statement=str((acceptance or {}).get("statement") or ""),
                source=str((acceptance or {}).get("source") or ""),
            )
        )
    return consolidate(findings)


def finding_totals(findings: list[Finding]) -> str:
    rows = "".join(
        total(severity.capitalize(), sum(1 for finding in findings if not finding.accepted and finding.severity == severity))
        for severity in SEVERITIES
    )
    return f"<h2>Totals</h2><table>{rows}{total('Accepted', sum(1 for finding in findings if finding.accepted))}</table>"


def state_of(finding: Finding) -> str:
    return "accepted" if finding.accepted else "open"


def render_findings(findings: list[Finding], header: Header) -> str:
    rows = "".join(
        f'<tr><td class="{finding.severity.lower()}">{finding.severity}</td><td>{e(finding.external_id)}</td>'
        f"<td>{e(finding.location)}</td><td>{e(finding.title)}</td><td>{e(finding.installed)}</td><td>{e(finding.fixed)}</td>"
        f'<td class="{state_of(finding)}">{state_of(finding)}</td><td>{e(finding.statement)}</td><td>{e(finding.source)}</td></tr>'
        for finding in sorted(findings, key=rank)
    )
    table = (
        "<table><tr><th>Severity</th><th>ID</th><th>Location</th><th>Title</th><th>Installed</th><th>Fixed</th>"
        f"<th>State</th><th>Statement</th><th>Source</th></tr>{rows}</table>"
        if rows
        else "<p>No finding.</p>"
    )
    return page(header, "scan report", f"{finding_totals(findings)}<h2>Findings</h2>{table}")


# ---------------------------------------------------------------------------------------------
# DAST
# ---------------------------------------------------------------------------------------------


# Any word with a path, a query or a parameter in it, and any word that reads as a host. Too wide on
# purpose: a title losing a word costs nothing, the raw file has it, and a location must not get by.
LOCATION = re.compile(r"\S*[/?=]\S*|\b[A-Za-z][\w-]*(?:\.[\w-]+){2,}\S*|\b[\w-]+\.(?:com|net|org|io|dev)\b\S*")


def without_location(text: str) -> str:
    """security/dast/README.md: never a URL, a parameter or scanner evidence. Titles come from the
    scanners' rules and carry none today; this is what keeps it so whatever a scanner starts
    writing there."""
    return LOCATION.sub("[redacted]", text)


def render_dast(path: Path, manifest: Path | None, header: Header) -> str:
    """A finding's title, its level and whether it is accepted: named one by one, so that no other
    field of the file can ever reach the page, and nothing is read but this file and the manifest.
    The ID, the location, the statement and the source are read for the counts at most."""
    findings = read_findings(path)
    rows = "".join(
        f'<tr><td class="{finding.severity.lower()}">{finding.severity}</td><td>{e(without_location(finding.title))}</td>'
        f'<td class="{state_of(finding)}">{state_of(finding)}</td></tr>'
        for finding in sorted(findings, key=rank)
    )
    table = (
        f"<table><tr><th>Level</th><th>Title</th><th>State</th></tr>{rows}</table>" if rows else "<p>No finding.</p>"
    )
    body = f"{finding_totals(findings)}<h2>Findings</h2>{table}"
    if manifest is not None:
        document = read_json(manifest)
        body += (
            "<h2>Full report</h2><p>Kept in the private <code>yontrack/security-reports</code>, never attached.</p>"
            f"<dl><dt>Path</dt><dd><code>{e(document.get('reportPath'))}</code></dd>"
            f"<dt>SHA-256</dt><dd><code>{e(document.get('sha256'))}</code></dd></dl>"
        )
    return page(header, "scan report", body)


# ---------------------------------------------------------------------------------------------
# Search performance
# ---------------------------------------------------------------------------------------------


def render_search_perf(path: Path, header: Header) -> str:
    report = read_json(path)
    figures = "".join(
        f"<tr><td>{e(key)}</td><td>{e(value)}</td></tr>"
        for key, value in report.items()
        if isinstance(value, (int, float)) and not isinstance(value, bool)
    )
    body = f"<h2>Figures</h2><table><tr><th>Figure</th><th>Value</th></tr>{figures}</table>"
    details = report.get("details") if isinstance(report.get("details"), dict) else {}
    for title, name in (("Failures", "failures"), ("Over budget", "over_budget")):
        items = details.get(name) or []
        if items:
            body += f"<h2>{e(title)}</h2><ul>{''.join(f'<li>{e(item)}</li>' for item in items)}</ul>"
    return page(header, "search performance", body)


# ---------------------------------------------------------------------------------------------
# The command line
# ---------------------------------------------------------------------------------------------


def render(kind: str, source: str, manifest: Path | None, header: Header) -> str:
    if kind == "junit":
        return render_junit(source, header)
    if kind == "trivy":
        return render_findings(read_trivy(Path(source)), header)
    if kind == "findings":
        return render_findings(read_findings(Path(source)), header)
    if kind == "dast":
        return render_dast(Path(source), manifest, header)
    return render_search_perf(Path(source), header)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    match = commands.add_parser("match", help="prints the files of a pattern")
    match.add_argument("pattern")

    zipping = commands.add_parser("zip", help="bundles the files of a pattern, unmodified")
    zipping.add_argument("pattern")
    zipping.add_argument("--output", type=Path, required=True)

    rendering = commands.add_parser("render", help="renders one HTML report")
    rendering.add_argument("kind", choices=["junit", "trivy", "findings", "dast", "search-perf"])
    rendering.add_argument("input", help="pattern of the JUnit reports, or the path of the report")
    rendering.add_argument("--manifest", type=Path, help="dast: the manifest of the private report")
    rendering.add_argument("--stamp", required=True)
    rendering.add_argument("--build", required=True)
    rendering.add_argument("--commit", required=True)
    rendering.add_argument("--run-url", required=True)
    rendering.add_argument("--raw", type=Path, action="append", default=[], help="raw evidence, to hash in the header")
    rendering.add_argument("--output", type=Path, required=True)

    args = parser.parse_args(argv)
    try:
        if args.command == "match":
            for file in matching_files(args.pattern):
                print(file.as_posix())
        elif args.command == "zip":
            bundle(args.pattern, args.output)
        else:
            header = Header(args.stamp, args.build, args.commit, args.run_url, args.raw)
            document = render(args.kind, args.input, args.manifest, header)
            write_atomically(args.output, lambda temporary: temporary.write_text(document, encoding="utf-8"))
    except (EvidenceError, OSError, ValueError, AttributeError, TypeError, zipfile.BadZipFile) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
