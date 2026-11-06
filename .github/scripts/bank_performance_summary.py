#!/usr/bin/env python3
"""Publish allowlisted numeric measurements; raw emulator artifacts stay on the runner."""
import argparse
import csv
import io
import json
import math
from pathlib import Path
import re
import subprocess

RUN_FIELDS = ('run', 'activity_relaunch_ms', 'startup_main_max_gap_ms', 'bank_ready_ms',
              'composed_rows', 'bank_main_max_gap_ms', 'detail_ready_ms',
              'detail_main_max_gap_ms', 'activity_recreate_ms')
OPERATIONS = ('dao_transactions', 'dao_links_and_rent', 'counts', 'filter', 'summary', 'group', 'search', 'matcher', 'rent',
              'reverse_matcher', 'transaction_picker', 'last_month_assignment', 'transfer_matcher')
ROLES = {'main', 'worker', 'instrumentation', 'other'}
CALCULATIONS = {'rent_month_parser', 'receipt_matcher', 'rent_candidates', 'rent_matcher',
                'regex_pattern', 'regex_helper', 'view_model', 'bank_list', 'last_month_assignment', 'transfer_matcher'}
PROFILE_QUERY = """
SELECT CASE WHEN t.name='main' THEN 'main' WHEN t.name LIKE 'Default%' THEN 'worker'
 WHEN t.name LIKE 'Instr%' THEN 'instrumentation' ELSE 'other' END AS role,
 CASE WHEN s.name LIKE '%RentMonthParser%' THEN 'rent_month_parser'
 WHEN s.name LIKE '%BankReceiptMatcher%' THEN 'receipt_matcher'
 WHEN s.name LIKE '%BankRentCandidateFactory%' THEN 'rent_candidates'
 WHEN s.name LIKE '%BankRentMatcher%' THEN 'rent_matcher'
 WHEN s.name LIKE '%BankLastMonthAssignmentPolicy%' THEN 'last_month_assignment'
 WHEN s.name LIKE '%BankTransferMatcher%' THEN 'transfer_matcher'
 WHEN s.name LIKE '%ReceiptViewModel%' THEN 'view_model'
 WHEN s.name LIKE '%BankCompactUiPolicy%' THEN 'bank_list'
 WHEN s.name LIKE 'java.util.regex.Pattern%' OR s.name LIKE 'java/util/regex/Pattern%'
 THEN 'regex_pattern' ELSE 'regex_helper' END AS calculation,
 count(*) AS calls, round(max(s.dur)/1e6,2) AS max_wall_ms,
 round(sum(s.dur)/1e6,2) AS inclusive_wall_ms
FROM slice s JOIN thread_track tt ON tt.id=s.track_id JOIN thread t ON t.utid=tt.utid
WHERE s.dur>=0 AND (s.name LIKE '%BankReceiptMatcher%' OR s.name LIKE '%RentMonthParser%'
 OR s.name LIKE '%BankRentCandidateFactory%' OR s.name LIKE '%BankRentMatcher%'
 OR s.name LIKE '%ReceiptViewModel%' OR s.name LIKE '%BankCompactUiPolicy%'
 OR s.name LIKE '%BankLastMonthAssignmentPolicy%' OR s.name LIKE '%BankTransferMatcher%'
 OR s.name LIKE 'java.util.regex.%' OR s.name LIKE 'java/util/regex/%'
 OR s.name LIKE 'kotlin.text.Regex%' OR s.name LIKE 'kotlin/text/Regex%')
GROUP BY role,calculation;
"""
SCHEDULER_QUERY = """
SELECT CASE WHEN t.tid=p.pid THEN 'main' WHEN t.name LIKE 'Default%' THEN 'worker'
 WHEN t.name LIKE 'Instr%' THEN 'instrumentation' ELSE 'other' END AS role,
 count(DISTINCT p.upid) AS processes, count(*) AS slices,
 round(sum(s.dur)/1e6,2) AS cpu_ms, round(max(s.dur)/1e6,2) AS max_slice_ms
FROM sched s JOIN thread t USING(utid) JOIN process p USING(upid)
WHERE s.dur>0 AND (p.cmdline='com.aistudio.steuerassistent.vnhqkp'
 OR p.cmdline GLOB 'com.aistudio.steuerassistent.vnhqkp:*' OR p.name LIKE '%vnhqkp')
GROUP BY role;
"""


def numeric(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and 0 <= value < 1e12


def select_numbers(data, fields):
    return {key: data[key] for key in fields if numeric(data.get(key))}


def memory_values(text):
    values = {}
    for label, key in [('TOTAL PSS', 'total_pss_kb'), ('Java Heap', 'java_heap_kb'), ('Native Heap', 'native_heap_kb')]:
        match = re.search(r'^\s*' + label + r':\s*(\d+)', text, re.M)
        if match:
            values[key] = int(match[1])
    return values


def profile_rows(text):
    result = []
    for row in csv.DictReader(io.StringIO(text)):
        if row.get('role') not in ROLES or row.get('calculation') not in CALCULATIONS:
            raise ValueError('Unapproved profiling category')
        try:
            values = {key: float(row[key]) for key in ('calls', 'max_wall_ms', 'inclusive_wall_ms')}
        except (KeyError, TypeError, ValueError):
            raise ValueError('Invalid profiling measurement') from None
        if not all(numeric(value) for value in values.values()):
            raise ValueError('Invalid profiling measurement')
        result.append({'role': row['role'], 'calculation': row['calculation'], **values})
    return result


def scheduler_rows(text):
    result = []
    for row in csv.DictReader(io.StringIO(text)):
        if row.get('role') not in ROLES:
            raise ValueError('Unapproved scheduler role')
        try:
            values = {key: float(row[key]) for key in ('processes', 'slices', 'cpu_ms', 'max_slice_ms')}
        except (KeyError, TypeError, ValueError):
            raise ValueError('Invalid scheduler measurement') from None
        if not all(numeric(value) for value in values.values()):
            raise ValueError('Invalid scheduler measurement')
        result.append({'role': row['role'], **values})
    return result


def fault_values(text):
    # Counts describe the whole device log for this test, not proven app causes.
    return {'device_oom_mentions': text.count('java.lang.OutOfMemoryError'),
            'device_sigsegv_mentions': text.count('Fatal signal 11'),
            'device_sigabrt_mentions': text.count('Fatal signal 6'),
            'device_art_frame_mentions': text.count('libart.so'),
            'device_gpu_frame_mentions': sum(text.count(name) for name in ('libGLES', 'libEGL', 'libvulkan', 'libhwui')),
            'app_lmk_mentions': len(re.findall(r'lmkd.*Kill.*com\.aistudio\.steuerassistent\.vnhqkp', text)),
            'recorder_lmk_mentions': len(re.findall(r'lmkd.*Kill.*perfetto', text))}


def recorder_values(root, count):
    values = {}
    start = root / f'recorder-{count}.start-exit'
    if start.exists() and start.read_text().strip().isdigit():
        values['start_exit'] = int(start.read_text())
    output = root / f'recorder-{count}.stdout'
    if output.exists():
        values['pid_line_count'] = sum(bool(re.fullmatch(r'[0-9]+', line.strip())) for line in output.read_text().splitlines())
    states = []
    for phase in ('start', 'after_test', 'before_stop'):
        prefix = root / f'recorder-{count}-{phase}'
        def read(suffix):
            path = Path(str(prefix) + suffix)
            return path.read_text() if path.exists() else ''
        status = read('.status')
        if not any(read(suffix) for suffix in ('.comm', '.status', '.kill-exit')):
            continue
        state = re.search(r'^State:\s*([A-Z])', status, re.M)
        uid = re.search(r'^Uid:\s*(\d+)', status, re.M)
        row = {'phase': phase, 'is_perfetto': read('.comm').strip() == 'perfetto',
               'uid_is_shell': bool(uid and uid[1] == '2000'),
               'state': {'R': 'running', 'S': 'sleeping', 'D': 'io_wait', 'Z': 'zombie'}.get(state[1] if state else '', 'unavailable'),
               'kill_permission_error': any(term in read('.kill-error') for term in ('Operation not permitted', 'Permission denied'))}
        if read('.kill-exit').strip().isdigit():
            row['kill_exit'] = int(read('.kill-exit'))
        states.append(row)
    values['states'] = states
    return values


def read_json(path):
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError):
        return {}


def build_summary(root, revision, processor=None):
    if revision not in {'before', 'after'}:
        raise ValueError('Invalid revision')
    cases = []
    for count in (1250, 5000):
        source = next(root.rglob(f'bank-performance-{count}.json'), None)
        data = read_json(source) if source else {}
        runs = [select_numbers(row, RUN_FIELDS) for row in data.get('runs', []) if isinstance(row, dict)]
        complete = (len(runs) == 3 and {row.get('run') for row in runs} == {1, 2, 3}
                    and all(set(RUN_FIELDS) <= row.keys() for row in runs))
        case = {'count': count, 'status': 'complete' if complete else 'incomplete' if source else 'unavailable', 'runs': runs}
        case['recorder_control'] = recorder_values(root, count)
        device_log = root / f'logcat-{count}.txt'
        if device_log.exists():
            case['fault_mentions'] = fault_values(device_log.read_text())
        plan = next(root.rglob(f'bank-query-plans-{count}.txt'), None)
        if plan:
            text = plan.read_text()
            case['query_plans'] = {'scans': len(re.findall(r'\bSCAN\b', text)),
                                   'index_uses': len(re.findall(r'\bUSING (?:COVERING )?INDEX\b', text)),
                                   'temporary_sorts': text.count('USE TEMP B-TREE')}
        case['memory'] = [dict(run=run, **memory_values(path.read_text())) for run in (1, 2, 3)
                          for path in root.rglob(f'bank-perf-{count}-run-{run}-meminfo.txt')]
        case['frames'] = []
        for run in (1, 2, 3):
            for path in root.rglob(f'bank-perf-{count}-run-{run}-gfxinfo.txt'):
                values = {'run': run}
                for label, key in [('50th percentile', 'p50_ms'), ('90th percentile', 'p90_ms'),
                                   ('95th percentile', 'p95_ms'), ('99th percentile', 'p99_ms')]:
                    match = re.search(r'^\s*' + label + r':\s*(\d+)ms', path.read_text(), re.M)
                    if match:
                        values[key] = int(match[1])
                case['frames'].append(values)
        case['cold_start'] = []
        for run in (1, 2, 3):
            path = root / f'cold-start-{count}-{run}.txt'
            if path.exists():
                text = path.read_text()
                match = re.search(r'^TotalTime:\s*(\d+)\s*$', text, re.M)
                if match and re.search(r'^Status: ok\s*$', text, re.M):
                    memory = root / f'cold-start-{count}-{run}-meminfo.txt'
                    values = memory_values(memory.read_text()) if memory.exists() else {}
                    case['cold_start'].append({'run': run, 'total_time_ms': int(match[1]), **values})
        workloads = next(root.rglob(f'bank-workloads-{count}.json'), None)
        case['workloads'] = {op: select_numbers(values, ('wall_ms', 'cpu_ms'))
                             for op, values in read_json(workloads).items()
                             if op in OPERATIONS and isinstance(values, dict)} if workloads else {}
        case['trace_bytes'] = {}
        for kind, name in [('scheduler', f'bank-performance-{count}.pftrace'),
                           ('startup', f'bank-startup-{count}.trace'), ('methods', f'bank-profile-{count}.trace')]:
            path = next(root.rglob(name), None)
            if path:
                case['trace_bytes'][kind] = path.stat().st_size
        case['profiles'] = []
        if processor:
            trace = root / f'bank-performance-{count}.pftrace'
            if trace.exists():
                parsed = subprocess.run([str(processor), '-Q', SCHEDULER_QUERY, str(trace)], capture_output=True, text=True)
                cpu = {'parsed': parsed.returncode == 0,
                       'scope': 'entire_case_including_instrumentation_and_profiles'}
                if parsed.returncode == 0:
                    cpu['measurements'] = scheduler_rows(parsed.stdout)
                    cpu['matched_cpu_slices'] = bool(cpu['measurements'])
                case['scheduler'] = cpu
            for kind in ('profile', 'startup'):
                for trace in root.rglob(f'bank-{kind}-{count}.trace'):
                    parsed = subprocess.run([str(processor), '-Q', PROFILE_QUERY, str(trace)], capture_output=True, text=True)
                    entry = {'kind': kind, 'parsed': parsed.returncode == 0}
                    if parsed.returncode == 0:
                        entry['measurements'] = profile_rows(parsed.stdout)
                    case['profiles'].append(entry)
        cases.append(case)
    result = {'revision': revision, 'cases': cases}
    result['recorder_smoke'] = recorder_values(root, 'smoke')
    smoke_trace = root / 'bank-performance-smoke.pftrace'
    if smoke_trace.exists():
        result['recorder_smoke']['trace_bytes'] = smoke_trace.stat().st_size
    version = root / 'perfetto-version.txt'
    if version.exists():
        match = re.search(r'v(\d+)\.(\d+)', version.read_text())
        if match:
            result['recorder_version'] = {'major': int(match[1]), 'minor': int(match[2])}
    exit_code = root / 'exit-code.txt'
    if exit_code.exists() and re.fullmatch(r'\d+\s*', exit_code.read_text()):
        result['instrumentation_exit_code'] = int(exit_code.read_text())
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--revision', choices=('before', 'after'), required=True)
    parser.add_argument('--trace-processor', type=Path)
    args = parser.parse_args()
    output = build_summary(args.root, args.revision, args.trace_processor)
    destination = args.root / 'public' / 'summary.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(output, indent=2, allow_nan=False) + '\n')
    print('BANK_NUMERIC_SUMMARY ' + json.dumps(output, allow_nan=False))


if __name__ == '__main__':
    main()
