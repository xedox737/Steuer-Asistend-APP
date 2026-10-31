import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verified_product_ci import require_verified_main_run, verify_current_event


class VerifiedProductCiTests(unittest.TestCase):
    repository = "synthetic-owner/synthetic-product"
    commit = "a" * 40

    def run_data(self):
        return dict(id=12, status="completed", conclusion="success", event="push", head_branch="main",
                    head_sha=self.commit, path=".github/workflows/android-ci.yml", name="Android CI",
                    head_repository=dict(full_name=self.repository))

    def jobs(self):
        return [dict(name=name, status="completed", conclusion="success")
                for name in ["verify", "android-emulator-e2e"]]

    def test_complete_exact_main_ci_is_accepted(self):
        require_verified_main_run(self.run_data(), self.jobs(), self.repository, self.commit)

    def test_other_commit_pr_branch_workflow_or_repository_cannot_publish(self):
        variants = [dict(head_sha="b"*40), dict(event="pull_request"), dict(head_branch="feature"),
                    dict(path="other.yml"), dict(conclusion="failure"), dict(status="in_progress"),
                    dict(head_repository=dict(full_name="other/product"))]
        for change in variants:
            with self.subTest(change=change), self.assertRaises(ValueError):
                require_verified_main_run(self.run_data() | change, self.jobs(), self.repository, self.commit)

    def test_missing_failed_skipped_or_unfinished_emulator_job_blocks_publication(self):
        for conclusion in ["failure", "skipped", "cancelled", None]:
            jobs = self.jobs(); jobs[1]["conclusion"] = conclusion
            with self.subTest(conclusion=conclusion), self.assertRaises(ValueError):
                require_verified_main_run(self.run_data(), jobs, self.repository, self.commit)
        with self.assertRaises(ValueError):
            require_verified_main_run(self.run_data(), self.jobs()[:1], self.repository, self.commit)

    def test_workflow_run_is_rechecked_from_api_instead_of_trusting_event_success(self):
        env = dict(GITHUB_REPOSITORY=self.repository, VERIFIED_PRODUCT_SHA=self.commit, GITHUB_EVENT_NAME="workflow_run")
        responses = {f"/repos/{self.repository}/actions/runs/12": self.run_data(),
                     f"/repos/{self.repository}/actions/runs/12/jobs?per_page=100": dict(jobs=self.jobs())}
        self.assertEqual(12, verify_current_event(env, dict(workflow_run=dict(id=12)), responses.__getitem__))
        responses[f"/repos/{self.repository}/actions/runs/12"]["conclusion"] = "failure"
        with self.assertRaises(ValueError):
            verify_current_event(env, dict(workflow_run=dict(id=12)), responses.__getitem__)

    def test_manual_main_run_requires_existing_complete_ci_and_other_events_are_rejected(self):
        env = dict(GITHUB_REPOSITORY=self.repository, VERIFIED_PRODUCT_SHA=self.commit,
                   GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_REF="refs/heads/main")
        def fetch(path):
            return dict(jobs=self.jobs()) if "/jobs?" in path else dict(workflow_runs=[self.run_data()])
        self.assertEqual(12, verify_current_event(env, {}, fetch))
        with self.assertRaises(ValueError):
            verify_current_event(env, {}, lambda path: dict(workflow_runs=[]))
        with self.assertRaises(ValueError):
            verify_current_event(env | dict(GITHUB_REF="refs/heads/feature"), {}, fetch)
