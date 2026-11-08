import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "run-osv-ci.py"


class OsvPolicyTest(unittest.TestCase):
    def scan(self, status, ecosystems, advisory=True, invalid=False):
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "result.json"
            payload = {"results": [{"packages": [
                {"package": {"ecosystem": ecosystem}, "vulnerabilities": [{"id": "TEST-1"}]}
                for ecosystem in ecosystems]}]}
            scanner = "import sys; from pathlib import Path; Path(sys.argv[2]).write_text(sys.argv[3]); sys.exit(int(sys.argv[4]))"
            return subprocess.run(
                [sys.executable, str(SCRIPT), sys.executable, "-c", scanner,
                 "--output-file", str(report), "invalid" if invalid else json.dumps(payload), str(status)],
                env={**os.environ, "OSV_MAVEN_ADVISORY": str(advisory).lower()},
                capture_output=True, text=True,
            ).returncode

    def test_daily_work_reports_maven_findings_including_in_container(self):
        self.assertEqual(0, self.scan(1, ["Maven"]))
        self.assertEqual(0, self.scan(0, []))

    def test_release_and_non_maven_findings_remain_blocking(self):
        self.assertNotEqual(0, self.scan(1, ["Maven"], advisory=False))
        for ecosystem in ("Red Hat", "Alpine", "unknown"):
            self.assertNotEqual(0, self.scan(1, ["Maven", ecosystem]))

    def test_execution_errors_and_incomplete_reports_fail(self):
        self.assertNotEqual(0, self.scan(128, ["Maven"]))
        self.assertNotEqual(0, self.scan(1, []))
        self.assertNotEqual(0, self.scan(1, [], invalid=True))
