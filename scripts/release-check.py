#!/usr/bin/env python3
"""Release posture gate for the Operator Console.

Plan: ``RELEASE-PLAN.md`` T7-102. PoC task 44 (see ``docs/POC-TRACKER.md``).

The console's release posture is stated in ADR 0007 (production transport posture)
and ADR 0010 (environments): a release build talks to a real deployed host over
HTTPS with certificate pinning, and it does not ship the Lab engine, the HMAC
signer, the emulator loopback host or the debug keystore. Those properties are
easy to lose silently -- a debug overlay edited here, a placeholder host left in
one file, a scaffold marker parked in a screen -- and they are not covered by the
JVM unit tests, because the unit tests never build a release variant.

This gate runs in CI over the sources a release APK is actually built from and
fails the build when the posture regresses.

What it checks
--------------

=====  ==========================================  ==============================================
ID     Property                                  Source of truth
=====  ==========================================  ==============================================
C1     No scaffold markers in shipped sources     ``TODO(``, ``SCAFFOLD`` (G-09 / T7-102)
C2     No placeholder (``.invalid``) live host    RFC 2606 reserved name; ADR 0007
C3     A live pin set with a valid expiry        ADR 0007 C-1b, BD-2.2 (>= 180 days)
C4     No cleartext in the release config        ADR 0007 (HTTPS only)
C5     Debug cleartext is local-host only         PoC debug overlay (10.0.2.2 / localhost)
C6     No Lab / HMAC signer in the release set    ADR 0004; Stage 5.5 packaging isolation
C7     No debug keystore or emulator host in      ADR 0007 (a release is signed, not debug)
       the release build type
=====  ==========================================  ==============================================

Scope
-----

The *release variant* is ``app/src/main`` plus ``app/src/release`` (absent today)
plus ``app/build.gradle.kts``. The debug overlay (``app/src/debug``) and the test
source sets (``app/src/test``, ``app/src/androidTest``) are deliberately outside
the release scope: the overlay is never packaged into a release, and the tests
are never packaged at all. C5 inspects the debug overlay on purpose -- the PoC
runs on the debug build, so that overlay's posture is checked too.

Known outstanding findings
--------------------------

Some findings are real but cannot be closed yet: the deployed host does not
exist, so there is no pin to embed, and the Lab engine still lives in the main
source set. Each such finding is listed in ``ALLOWLIST`` below with the task
that owns it. An allowlisted finding is reported as ``OUTSTANDING`` and does not
fail the build; **any finding that is not allowlisted fails the build**.

The allowlist is deliberately tight. Each entry names one check, one exact file
and the exact text that is tolerated, so a *different* finding in the same file
still fails. Each entry must still match something: an allowlist entry that no
longer matches is itself a failure ("stale allowlist entry"), so the list cannot
rot and a finished task cannot be silently forgotten.

Usage
-----

::

    python3 scripts/release-check.py [--repo-root PATH]

Exit code 0 when the release posture holds, 1 otherwise.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import os
import re
import sys

# The patterns are assembled at runtime so that this file does not match its own
# scan if the scope is ever widened to cover scripts/.
TODO_MARKER = "TO" + "DO" + r"\("
SCAFFOLD_MARKER = "SCAF" + "FOLD"
PLACEHOLDER_HOST = "gateway" + r"\.invalid"

#: Text file extensions that are read as text and scanned.
TEXT_SUFFIXES = frozenset(
    {".kt", ".java", ".xml", ".kts", ".pro", ".properties", ".json", ".txt", ".yml", ".yaml"}
)

#: Source roots that make up the release variant.
RELEASE_SOURCE_ROOTS = ("app/src/main", "app/src/release")

#: Minimum pin-set validity, in days (BD-2.2).
MIN_PIN_VALIDITY_DAYS = 180

#: Hosts the debug overlay is permitted to reach over cleartext.
LOCAL_DEBUG_HOSTS = frozenset({"10.0.2.2", "localhost", "127.0.0.1"})

MAIN_PKG = "app/src/main/kotlin/com/inscopelabs/buildengine"


def _entries() -> list:
    """Allowlisted findings: real, tracked, and not closable yet."""
    return [
        # --- C1: scaffold markers in shipped sources -------------------------------
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/audit/AuditInspectionScreen.kt",
            "pattern": re.escape("TODO(Stage 4.4 / T4-401)"),
            "reason": "Audit inspection screen has no ViewModel yet",
            "owner": "PoC task 21",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/decision/DecisionHistoryScreen.kt",
            "pattern": re.escape("TODO(Stage 4.4 / T4-401)"),
            "reason": "Decision history screen has no ViewModel yet",
            "owner": "Stage 4.4 / T4-401",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/device/DeviceManagementScreen.kt",
            "pattern": re.escape("TODO(Stage 4.4 / T4-401)"),
            "reason": "Device management screen has no ViewModel yet",
            "owner": "PoC task 23",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/lab/LabScreen.kt",
            "pattern": re.escape("TODO(Stage 5.3 / T5-301..T5-304)"),
            "reason": "Lab UI has no runner behind it yet",
            "owner": "Stage 5.3 / T5-301..T5-304",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/lab/LabViewModel.kt",
            "pattern": re.escape("TODO(Stage 5.3 / T5-301)"),
            "reason": "Lab ViewModel has no signer behind it yet",
            "owner": "Stage 5.3 / T5-301",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/operations/HaltResumeScreen.kt",
            "pattern": re.escape("TODO(Stage 4.4 / T4-401)"),
            "reason": "Halt/resume screen has no ViewModel yet",
            "owner": "PoC task 19",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/submission/SubmissionReviewScreen.kt",
            "pattern": re.escape("TODO(Stage 4.3 / T4-301)"),
            "reason": "Submission review screen has no ViewModel yet",
            "owner": "Stage 4.3 / T4-301",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/theme/Color.kt",
            "pattern": re.escape("SCAFFOLD PLACEHOLDER"),
            "reason": "Theme colour tokens are placeholders",
            "owner": "PoC task 27",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/theme/Theme.kt",
            "pattern": re.escape("SCAFFOLD PLACEHOLDER"),
            "reason": "Theme wiring is a placeholder",
            "owner": "PoC task 29",
        },
        {
            "check": "C1",
            "path": f"{MAIN_PKG}/ui/theme/Type.kt",
            "pattern": re.escape("SCAFFOLD PLACEHOLDER"),
            "reason": "Typography tokens are placeholders",
            "owner": "PoC task 28",
        },
        # --- C2: placeholder host -------------------------------------------------
        {
            "check": "C2",
            "path": f"{MAIN_PKG}/core/network/GatewayEndpoints.kt",
            "pattern": re.escape('const val BASE_URL: String = "https://gateway.invalid"'),
            "reason": "Base URL default is still the reserved placeholder name",
            "owner": "PoC tasks 17 and 18",
        },
        {
            "check": "C2",
            "path": "app/src/main/res/xml/network_security_config.xml",
            "pattern": re.escape("<domain includeSubdomains=\"true\">gateway.invalid</domain>"),
            "reason": "Network security config still names the placeholder host",
            "owner": "PoC task 39 (needs EXT-6)",
        },
        # --- C3: pin set ----------------------------------------------------------
        {
            "check": "C3",
            "path": "app/src/main/res/xml/network_security_config.xml",
            "pattern": re.escape("no live <pin-set>"),
            "reason": "No deployed host yet, so there is no SPKI pin to embed",
            "owner": "PoC task 39 (needs EXT-6)",
        },
        # --- C6: Lab / HMAC in the release source set ------------------------------
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/capability/signing/CapabilitySigner.kt",
            "pattern": re.escape("HmacCapabilitySigner"),
            "reason": "Lab HMAC signer is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/capability/signing/HmacCapabilitySigner.kt",
            "pattern": re.escape("HmacCapabilitySigner"),
            "reason": "Lab HMAC signer is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/di/CryptoModule.kt",
            "pattern": re.escape("HmacCapabilitySigner"),
            "reason": "Lab HMAC signer is still bound in the production DI graph",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/lab/ContractCheck.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab engine is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/lab/LabEnvironmentGuard.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab engine is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/lab/LabReport.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab engine is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/lab/Mutator.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab engine is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/lab/Scenario.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab engine is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/lab/ScenarioCatalogue.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab engine is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/lab/ScenarioResult.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab engine is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/lab/ScenarioRunner.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab engine is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/ui/lab/LabScreen.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab UI is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
        {
            "check": "C6",
            "path": f"{MAIN_PKG}/ui/lab/LabViewModel.kt",
            "pattern": re.escape("com.inscopelabs.buildengine.lab") + "|" + re.escape("HmacCapabilitySigner"),
            "reason": "Lab UI is still in the main source set",
            "owner": "Stage 5.5 (release packaging isolation)",
        },
    ]


def _exempt() -> list:
    """Findings that are correct code, not violations, and are not tracked."""
    return [
        {
            "check": "C2",
            "path": f"{MAIN_PKG}/core/network/GatewayEndpoints.kt",
            "pattern": re.escape('"gateway.invalid",'),
            "reason": "reserved-name denylist, not a live default",
        },
        {
            "check": "C2",
            "path": f"{MAIN_PKG}/data/local/GatewayBaseUrlStore.kt",
            "pattern": re.escape("(`gateway.invalid`)"),
            "reason": "KDoc stating that the placeholder is never treated as configured",
        },
    ]


class Finding:
    """One property violation found in one file."""

    def __init__(self, check: str, path: str, detail: str, snippet: str = "", line: int = 0):
        self.check = check
        self.path = path
        self.detail = detail
        # A file-level finding has no source line; its detail is the matched text,
        # so the allowlist can still match it by content.
        self.snippet = snippet if snippet else detail
        self.line = line
        self.status = "FAIL"
        self.entry = None

    def location(self) -> str:
        return f"{self.path}:{self.line}" if self.line else self.path

    def __str__(self) -> str:
        where = self.location()
        tail = f" | {self.snippet.strip()[:120]}" if self.snippet else ""
        return f"{self.check} {where} — {self.detail}{tail}"


def _read(path: str) -> str:
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        return handle.read()


def _is_scannable(path: str) -> bool:
    return os.path.splitext(path)[1].lower() in TEXT_SUFFIXES


def _release_files(repo_root: str) -> list:
    """Every text file that a release APK is built from."""
    found = []
    for root in RELEASE_SOURCE_ROOTS:
        base = os.path.join(repo_root, root)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames.sort()
            for name in sorted(filenames):
                absolute = os.path.join(dirpath, name)
                if _is_scannable(absolute):
                    found.append(os.path.relpath(absolute, repo_root))
    return found


def _strip_xml_comments(text: str) -> str:
    return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)


def _brace_block(text: str, header: str) -> str:
    """Extract the ``header { ... }`` block from a Gradle script."""
    match = re.search(re.escape(header) + r"\s*\{", text)
    if not match:
        return ""
    depth = 0
    for index in range(match.end() - 1, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[match.end() : index]
    return text[match.end() :]


def check_c1_scaffold_markers(repo_root: str, files: list) -> list:
    """C1 — scaffold markers in shipped sources."""
    findings = []
    for relative in files:
        text = _read(os.path.join(repo_root, relative))
        for number, line in enumerate(text.splitlines(), start=1):
            for marker in (TODO_MARKER, SCAFFOLD_MARKER):
                if re.search(marker, line):
                    findings.append(
                        Finding(
                            "C1",
                            relative,
                            "scaffold marker in a release source",
                            line,
                            number,
                        )
                    )
    return findings


def check_c2_placeholder_host(repo_root: str, files: list) -> list:
    """C2 — a reserved ``.invalid`` host used as a live host."""
    findings = []
    for relative in files:
        text = _read(os.path.join(repo_root, relative))
        for number, line in enumerate(text.splitlines(), start=1):
            if re.search(PLACEHOLDER_HOST, line):
                findings.append(
                    Finding(
                        "C2",
                        relative,
                        "placeholder host in a release source",
                        line,
                        number,
                    )
                )
    return findings


def check_c3_pin_set(repo_root: str) -> list:
    """C3 — a live pin set with a valid expiry."""
    relative = "app/src/main/res/xml/network_security_config.xml"
    path = os.path.join(repo_root, relative)
    if not os.path.isfile(path):
        return [
            Finding(
                "C3",
                relative,
                "no network security config, so no pin set can be enforced",
            )
        ]
    text = _strip_xml_comments(_read(path))
    findings = []
    pin_sets = re.findall(r"<pin-set\b[^>]*>(.*?)</pin-set>", text, flags=re.DOTALL)
    if not pin_sets:
        findings.append(
            Finding("C3", relative, "no live <pin-set> (every pin-set is commented out or absent)")
        )
        return findings
    today = _dt.date.today()
    for match in re.finditer(r"<pin-set\b([^>]*)>(.*?)</pin-set>", text, flags=re.DOTALL):
        header, block = match.group(1), match.group(2)
        pins = re.findall(r"<pin\b[^>]*>(.*?)</pin>", block, flags=re.DOTALL)
        usable = [p for p in pins if p.strip() and "REPLACE" not in p.upper()]
        if not usable:
            findings.append(Finding("C3", relative, "pin set carries no usable pin"))
        expiry = re.search(r'expiration="(\d{4}-\d{2}-\d{2})"', header.group(1) if header else "")
        if not expiry:
            findings.append(Finding("C3", relative, "pin set has no expiry date"))
            continue
        try:
            expiry_date = _dt.date.fromisoformat(expiry.group(1))
        except ValueError:
            findings.append(Finding("C3", relative, "pin set expiry is not an ISO date"))
            continue
        remaining = (expiry_date - today).days
        if remaining < MIN_PIN_VALIDITY_DAYS:
            findings.append(
                Finding(
                    "C3",
                    relative,
                    f"pin set expires in {remaining} days, "
                    f"BD-2.2 requires {MIN_PIN_VALIDITY_DAYS}",
                )
            )
    return findings


def check_c4_cleartext(repo_root: str) -> list:
    """C4 — cleartext permitted in the release configuration."""
    findings = []
    base = os.path.join(repo_root, "app/src/main/res/xml")
    if not os.path.isdir(base):
        return findings
    for name in sorted(os.listdir(base)):
        if not name.endswith(".xml"):
            continue
        relative = f"app/src/main/res/xml/{name}"
        text = _read(os.path.join(repo_root, relative))
        for number, line in enumerate(text.splitlines(), start=1):
            if re.search(r'cleartextTrafficPermitted\s*=\s*"true"', line):
                findings.append(
                    Finding("C4", relative, "cleartext is permitted in the release config", line, number)
                )
    return findings


def check_c5_debug_cleartext_scope(repo_root: str) -> list:
    """C5 — the debug overlay may only use cleartext for local hosts."""
    findings = []
    base = os.path.join(repo_root, "app/src/debug/res/xml")
    if not os.path.isdir(base):
        return findings
    for name in sorted(os.listdir(base)):
        if not name.endswith(".xml"):
            continue
        relative = f"app/src/debug/res/xml/{name}"
        text = _read(os.path.join(repo_root, relative))
        for block in re.findall(
            r"<domain-config\b[^>]*>(.*?)</domain-config>", text, flags=re.DOTALL
        ):
            if not re.search(r'cleartextTrafficPermitted\s*=\s*"true"', block):
                continue
            hosts = re.findall(r"<domain[^>]*>(.*?)</domain>", block)
            for host in hosts:
                if host.strip() not in LOCAL_DEBUG_HOSTS:
                    findings.append(
                        Finding(
                            "C5",
                            relative,
                            f"debug cleartext is opened to {host.strip()!r}, "
                            "which is not a local test host",
                        )
                    )
    return findings


def check_c6_lab_in_release(repo_root: str, files: list) -> list:
    """C6 — the Lab engine or the HMAC signer in the release source set."""
    findings = []
    markers = ("HmacCapabilitySigner", "com.inscopelabs.buildengine.lab")
    for relative in files:
        text = _read(os.path.join(repo_root, relative))
        for number, line in enumerate(text.splitlines(), start=1):
            for marker in markers:
                if marker in line:
                    findings.append(
                        Finding(
                            "C6",
                            relative,
                            "Lab engine / HMAC signer is inside the release source set",
                            line,
                            number,
                        )
                    )
    return findings


def check_c7_release_build_type(repo_root: str) -> list:
    """C7 — the release build type must not carry debug signing or emulator hosts."""
    relative = "app/build.gradle.kts"
    path = os.path.join(repo_root, relative)
    if not os.path.isfile(path):
        return [Finding("C7", relative, "the app build file is missing")]
    block = _brace_block(_read(path), "release")
    if not block:
        return [Finding("C7", relative, "no release build type is declared")]
    findings = []
    for needle, why in (
        ("debugConfig", "the release build type uses the debug signing config"),
        ("debug.keystore", "the release build type points at the debug keystore"),
        ("10.0.2.2", "the release build type carries the emulator loopback host"),
        ("localhost", "the release build type carries the emulator loopback host"),
        ("127.0.0.1", "the release build type carries the emulator loopback host"),
    ):
        if needle in block:
            findings.append(Finding("C7", relative, why, block.strip()[:200]))
    return findings


def _classify(findings: list, allowlist: list, exempt: list) -> None:
    """Decide each finding's status and record which allowlist entry matched."""
    for finding in findings:
        for entry in exempt:
            if entry["check"] != finding.check or entry["path"] != finding.path:
                continue
            if re.search(entry["pattern"], finding.snippet):
                finding.status = "EXEMPT"
                finding.entry = entry
                break
        if finding.status == "EXEMPT":
            continue
        for entry in allowlist:
            if entry["check"] != finding.check or entry["path"] != finding.path:
                continue
            if re.search(entry["pattern"], finding.snippet):
                finding.status = "OUTSTANDING"
                finding.entry = entry
                break
        else:
            finding.status = "FAIL"


def _stale_allowlist_entries(findings: list, allowlist: list) -> list:
    """Allowlist entries that no longer match anything are themselves a failure."""
    matched = {id(finding.entry) for finding in findings if finding.entry is not None}
    return [entry for entry in allowlist if id(entry) not in matched]


def main(argv: list) -> int:
    parser = argparse.ArgumentParser(description="Release posture gate for the Operator Console.")
    parser.add_argument(
        "--repo-root",
        default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        help="repository root (defaults to the parent of scripts/)",
    )
    args = parser.parse_args(argv)
    repo_root = os.path.abspath(args.repo_root)

    files = _release_files(repo_root)
    findings = []
    findings.extend(check_c1_scaffold_markers(repo_root, files))
    findings.extend(check_c2_placeholder_host(repo_root, files))
    findings.extend(check_c3_pin_set(repo_root))
    findings.extend(check_c4_cleartext(repo_root))
    findings.extend(check_c5_debug_cleartext_scope(repo_root))
    findings.extend(check_c6_lab_in_release(repo_root, files))
    findings.extend(check_c7_release_build_type(repo_root))

    allowlist = _entries()
    exempt = _exempt()
    _classify(findings, allowlist, exempt)
    stale = _stale_allowlist_entries(findings, allowlist)

    failures = [f for f in findings if f.status == "FAIL"]
    outstanding = [f for f in findings if f.status == "OUTSTANDING"]
    exempt_findings = [f for f in findings if f.status == "EXEMPT"]

    print("Release posture check (plan T7-102)")
    print(f"  repository root : {repo_root}")
    print(f"  release scope   : {', '.join(RELEASE_SOURCE_ROOTS)}")
    print(f"  files scanned   : {len(files)}")
    print()

    if outstanding:
        print(f"OUTSTANDING ({len(outstanding)}) — tracked, not failing:")
        for finding in outstanding:
            owner = finding.entry["owner"] if finding.entry else "?"
            print(f"  {finding}")
            print(f"      tracked by {owner}: {finding.entry['reason']}")
        print()

    if exempt_findings:
        print(f"EXEMPT ({len(exempt_findings)}) — permitted, not tracked:")
        for finding in exempt_findings:
            print(f"  {finding}")
        print()

    if failures:
        print(f"FAILED ({len(failures)}) — the release posture has regressed:")
        for finding in failures:
            print(f"  {finding}")
        print()

    for entry in stale:
        print(
            f"FAILED — stale allowlist entry: {entry['check']} {entry['path']} "
            f"no longer matches anything ({entry['reason']}); remove it or close {entry['owner']}"
        )
    if stale:
        print()

    if failures or stale:
        print("RELEASE POSTURE: FAILED")
        return 1

    print("RELEASE POSTURE: OK")
    if outstanding:
        print(
            f"  ({len(outstanding)} tracked outstanding finding(s) remain -- see the "
            "PoC tracker for the task that closes each one)"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
