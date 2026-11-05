import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / 'bank_performance_summary.py'
spec = importlib.util.spec_from_file_location('bank_summary', MODULE)
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


class BankPerformanceSummaryTest(unittest.TestCase):
    def test_only_allowed_numeric_measurements_are_exported(self):
        with tempfile.TemporaryDirectory() as root:
            p = Path(root) / 'bank-performance-1250.json'
            p.write_text(json.dumps({'count': 1250, 'runs': [{'run': 1, 'bank_ready_ms': 250,
                'composed_rows': 4, 'counterparty': 'PRIVATE-SENTINEL', 'receipt_id': 'PRIVATE-SENTINEL'}]}))
            Path(root, 'bank-query-plans-1250.txt').write_text(
                'SELECT PRIVATE-SENTINEL\nSCAN records USING INDEX index_fixture; USE TEMP B-TREE FOR ORDER BY\n')
            data = summary.build_summary(Path(root), 'after')
            encoded = json.dumps(data)
            self.assertNotIn('PRIVATE-SENTINEL', encoded)
            self.assertNotIn('counterparty', encoded)
            self.assertEqual(250, data['cases'][0]['runs'][0]['bank_ready_ms'])
            self.assertEqual('incomplete', data['cases'][0]['status'])
            self.assertEqual({'scans': 1, 'index_uses': 1, 'temporary_sorts': 1}, data['cases'][0]['query_plans'])

    def test_missing_or_non_numeric_values_never_become_success_or_zero_times(self):
        with tempfile.TemporaryDirectory() as root:
            Path(root, 'bank-performance-5000.json').write_text(json.dumps({'count': 5000,
                'runs': [{'run': 1, 'bank_ready_ms': 'PRIVATE-SENTINEL', 'detail_ready_ms': float('nan')}]}))
            data = summary.build_summary(Path(root), 'after')
            self.assertEqual('unavailable', data['cases'][0]['status'])
            self.assertEqual({'run': 1}, data['cases'][1]['runs'][0])
            self.assertNotIn('PRIVATE-SENTINEL', json.dumps(data))

    def test_memory_summary_drops_all_other_text(self):
        values = summary.memory_values('TOTAL PSS: 12345\n Java Heap: 2500\n Native Heap: 1500\nBank: PRIVATE-SENTINEL')
        self.assertEqual({'total_pss_kb': 12345, 'java_heap_kb': 2500, 'native_heap_kb': 1500}, values)

    def test_profile_csv_rejects_unapproved_labels(self):
        with self.assertRaises(ValueError):
            summary.profile_rows('role,calculation,calls,max_wall_ms,inclusive_wall_ms\nmain,PRIVATE-SENTINEL,1,2,2\n')

    def test_profile_csv_preserves_only_numeric_aggregates_and_known_labels(self):
        rows = summary.profile_rows('role,calculation,calls,max_wall_ms,inclusive_wall_ms\nworker,receipt_matcher,3,2.5,5.0\n')
        self.assertEqual([{'role': 'worker', 'calculation': 'receipt_matcher', 'calls': 3.0,
            'max_wall_ms': 2.5, 'inclusive_wall_ms': 5.0}], rows)

    def test_scheduler_csv_exports_cpu_aggregates_without_process_identifiers(self):
        rows = summary.scheduler_rows('role,processes,slices,cpu_ms,max_slice_ms\nmain,3,12,25.0,4.0\n')
        self.assertEqual([{'role': 'main', 'processes': 3.0, 'slices': 12.0,
                           'cpu_ms': 25.0, 'max_slice_ms': 4.0}], rows)

    def test_scheduler_csv_rejects_dynamic_or_private_labels(self):
        with self.assertRaises(ValueError):
            summary.scheduler_rows('role,processes,slices,cpu_ms,max_slice_ms\nPRIVATE-SENTINEL,1,2,3,4\n')

    def test_fault_report_contains_only_known_counts(self):
        values = summary.fault_values('java.lang.OutOfMemoryError PRIVATE-SENTINEL\n'
            'Fatal signal 11 (SIGSEGV)\nbacktrace libart.so\n'
            "lmkd: Kill 'perfetto'\nlmkd: Kill 'com.aistudio.steuerassistent.vnhqkp'\n")
        self.assertEqual(1, values['device_oom_mentions'])
        self.assertEqual(1, values['device_sigsegv_mentions'])
        self.assertEqual(1, values['recorder_lmk_mentions'])
        self.assertEqual(1, values['app_lmk_mentions'])
        self.assertNotIn('PRIVATE-SENTINEL', json.dumps(values))


if __name__ == '__main__':
    unittest.main()
