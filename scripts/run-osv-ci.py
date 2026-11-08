"""Apply the development-branch Maven advisory policy to a fresh OSV report."""
import json
import os
from pathlib import Path
import subprocess
import sys


def maven_findings(report):
    results = report["results"]
    if not isinstance(results, list):
        raise ValueError("expected OSV results")
    findings = set()
    for result in results:
        for package in result["packages"]:
            if package.get("license_violations") or package.get("violations"):
                return set()
            for vuln in package.get("vulnerabilities", []):
                if package["package"]["ecosystem"] != "Maven":
                    return set()
                findings.add(vuln["id"])
    return findings


def main(command):
    output = Path(command[command.index("--output-file") + 1])
    output.unlink(missing_ok=True)
    status = subprocess.run(command, check=False).returncode
    if status not in (0, 1):
        return status
    try:
        report = json.loads(output.read_text())
        if status == 0:
            return 0 if "results" in report else 1
        if os.environ.get("OSV_MAVEN_ADVISORY") == "true":
            findings = maven_findings(report)
            if findings:
                print(f"::warning::{len(findings)} Maven advisories. See {output.name}.")
                return 0
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"Invalid OSV report: {error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
