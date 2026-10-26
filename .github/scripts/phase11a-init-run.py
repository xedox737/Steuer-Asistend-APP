#!/usr/bin/env python3
"""Create a local, immutable run workspace; all practical tests start open."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re

TESTS = ("DRIVE", "DEVICE2", "PICKER", "VIEWER", "OFFLINE", "INTERRUPT", "MANUAL", "RESTART", "RECREATE")
p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--run-id", required=True)
p.add_argument("--commit", required=True)
p.add_argument("--units", type=int, choices=(17, 21), required=True)
p.add_argument("--output", type=Path, required=True)
a = p.parse_args()
if not re.fullmatch(r"P11A-\d{4}-\d{2}-\d{2}-[A-Za-z0-9-]+", a.run_id) or not re.fullmatch(r"[0-9a-f]{40}", a.commit):
    p.error("invalid run-id/commit")
directory = a.output / a.run_id
directory.mkdir(parents=True, exist_ok=False)
device = dict(model=None, android=None, appVersion=None, commit=a.commit, androidUser=None, evidence=None)
record = dict(runId=a.run_id, createdAt=datetime.now(timezone.utc).isoformat(), appCommit=a.commit, units=a.units,
              deviceA=device, deviceB=device.copy(), tests={f"P11A-{t}-01": "NOCH OFFEN" for t in TESTS},
              decision="KEINE PRAKTISCHE PORTFOLIOFREIGABE", findings=[])
(directory / "run.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
text = f"# {a.run_id} – Geräteprotokoll\n\nApp-Commit: `{a.commit}`; 5 Objekte / {a.units} Einheiten.\n\n"
text += "Gerät A/B: Modell, Android-Version, App-Version, Commit, Android-User, APK-SHA256 und Installationsquelle ausfüllen.\n\n"
for test in TESTS:
    text += f"""## P11A-{test}-01

- Datum/UTC, Prüfer, Gerät/Profil: NOCH OFFEN
- App-Commit: {a.commit}
- Ausgangszustand und Baseline-SHA256: NOCH OFFEN
- Tatsächlich durchgeführte Schritte mit Uhrzeiten: NOCH OFFEN
- Erwartung / beobachtetes Ergebnis: NOCH OFFEN
- Start-/Zwischen-/End-/Fehlerscreenshot: NOCH OFFEN
- Logausschnitt / Run-ID / Backup-ID / Restore-ID: NOCH OFFEN
- After-Snapshot / SHA256 / Checker-Report: NOCH OFFEN
- IDs / Counts / Dubletten / Orphans / 60 Originalhashes / Contract-Differenzen: NOCH OFFEN
- Status: NOCH OFFEN

"""
text += "Befunde E11A-01 ff.: Priorität P0/P1/P2/P3, Reproduktion, Gerät/Android, Screenshot, Log, betroffene IDs, Datenverlust ja/nein, gesicherte Root Cause oder unbekannt. Keine stillen Fixes.\n\nEntscheidung: KEINE PRAKTISCHE PORTFOLIOFREIGABE\n"
(directory / "report.md").write_text(text)
print(directory)
