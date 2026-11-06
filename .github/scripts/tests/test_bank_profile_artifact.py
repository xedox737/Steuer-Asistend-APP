from pathlib import Path
import os
import subprocess
import tempfile
import unittest

HELPER = Path(__file__).parents[1] / 'bank-profile-artifact.sh'


class BankProfileArtifactTest(unittest.TestCase):
    def exercise(self, sizes):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adb = root / 'adb'
            adb.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
root = pathlib.Path(os.environ['BANK_FAKE_ROOT'])
sizes = json.loads(os.environ['BANK_FAKE_SIZES'])
state = root / 'polls'
index = int(state.read_text()) if state.exists() else 0
if sys.argv[1] == 'shell':
    state.write_text(str(index + 1))
    print(sizes[min(index, len(sizes)-1)])
elif sys.argv[1] == 'pull':
    pathlib.Path(sys.argv[3]).write_bytes(b'x' * sizes[min(index-1, len(sizes)-1)])
else:
    raise SystemExit(1)
''')
            adb.chmod(0o755)
            output = root / 'startup.trace'
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'],
                       BANK_FAKE_ROOT=directory, BANK_FAKE_SIZES=str(sizes))
            result = subprocess.run(['bash', '-c', 'source "$1"; bank_pull_flushed_profile remote "$2" 6 0',
                                     'test', str(HELPER), str(output)], env=env, capture_output=True, text=True)
            polls = root / 'polls'
            return result.returncode, output.stat().st_size if output.exists() else 0, int(polls.read_text()) if polls.exists() else 0

    def test_waits_for_async_flush_and_a_stable_nonempty_size(self):
        self.assertEqual((0, 128, 4), self.exercise([0, 0, 128, 128]))

    def test_growing_file_is_not_pulled_at_its_first_positive_size(self):
        self.assertEqual((0, 512, 5), self.exercise([0, 128, 256, 512, 512]))

    def test_empty_profile_remains_a_failure_after_bounded_polls(self):
        self.assertEqual((1, 0, 6), self.exercise([0]))


class PerfettoStopTest(unittest.TestCase):
    def exercise(self, alive_polls, attempts):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adb = root / 'adb'
            adb.write_text('''#!/usr/bin/env python3
import os, pathlib, sys
root = pathlib.Path(os.environ['BANK_FAKE_ROOT'])
alive = int(os.environ['BANK_FAKE_ALIVE'])
polls = root / 'polls'
signals = root / 'signals'
count = int(polls.read_text()) if polls.exists() else 0
args = sys.argv[1:]
if args[:3] == ['shell', 'kill', '-0']:
    raise SystemExit(1)  # Android may deny signull even for the live shell-owned recorder.
if args[:3] == ['shell', 'test', '-d']:
    polls.write_text(str(count+1))
    raise SystemExit(0 if count < alive else 1)
if args[:3] == ['shell', 'kill', '-TERM']:
    signals.write_text(str(int(signals.read_text())+1 if signals.exists() else 1))
elif args[:2] == ['shell', 'stat']:
    print(128)
elif args[0] == 'pull':
    if count <= alive: raise SystemExit(55)
    pathlib.Path(args[2]).write_bytes(b'x'*128)
else:
    raise SystemExit(1)
''')
            adb.chmod(0o755)
            output = root / 'scheduler.pftrace'
            env = dict(os.environ, PATH=str(root)+os.pathsep+os.environ['PATH'],
                       BANK_FAKE_ROOT=directory, BANK_FAKE_ALIVE=str(alive_polls))
            result = subprocess.run(['bash', '-c', 'source "$1"; bank_stop_perfetto_and_pull 123 remote "$2" "$3" 0',
                                     'test', str(HELPER), str(output), str(attempts)], env=env,
                                    capture_output=True, text=True)
            def value(name):
                p = root / name
                return int(p.read_text()) if p.exists() else 0
            return result.returncode, value('signals'), value('polls'), output.stat().st_size if output.exists() else 0

    def test_waits_past_five_polls_for_flush_and_sends_only_one_term(self):
        self.assertEqual((0, 1, 8, 128), self.exercise(7, 10))

    def test_stop_timeout_fails_without_copying_or_sending_a_second_term(self):
        self.assertEqual((1, 1, 7, 0), self.exercise(100, 6))

    def test_naturally_finished_capture_is_pulled_without_another_signal(self):
        self.assertEqual((0, 0, 1, 128), self.exercise(0, 6))


class RecorderPidTest(unittest.TestCase):
    def parse(self, output):
        return subprocess.run(['bash', '-c', 'source "$1"; bank_recorder_pid', 'test', str(HELPER)],
                              input=output, capture_output=True, text=True)

    def test_numeric_pid_is_read_even_with_a_trailing_status_line(self):
        result = self.parse('1234\r\nTracing started\n')
        self.assertEqual((0, '1234\n'), (result.returncode, result.stdout))

    def test_missing_pid_is_rejected(self):
        self.assertNotEqual(0, self.parse('Tracing started\n').returncode)

    def test_ambiguous_numeric_output_is_rejected(self):
        self.assertNotEqual(0, self.parse('1234\n5678\n').returncode)


if __name__ == '__main__':
    unittest.main()
