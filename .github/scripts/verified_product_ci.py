"""Only a successful main-branch CI for this exact product commit permits publication."""
import re


def require_verified_main_run(run, jobs, repository, commit):
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Invalid product commit")
    expected = dict(status="completed", conclusion="success", event="push", head_branch="main",
                    head_sha=commit, path=".github/workflows/android-ci.yml", name="Android CI")
    if any(run.get(key) != value for key, value in expected.items()):
        raise ValueError("Exact main-branch product CI has not succeeded")
    if run.get("head_repository", {}).get("full_name") != repository:
        raise ValueError("CI belongs to another repository")
    required = {"verify", "android-emulator-e2e"}
    if not required.issubset({job.get("name") for job in jobs}):
        raise ValueError("Required product CI jobs are missing")
    if any(job.get("status") != "completed" or job.get("conclusion") != "success" for job in jobs):
        raise ValueError("Product CI contains failed, skipped or unfinished jobs")


def verify_current_event(environment, event, fetch):
    repository = environment["GITHUB_REPOSITORY"]
    commit = environment["VERIFIED_PRODUCT_SHA"]
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Invalid product commit")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid product repository")
    prefix = f"/repos/{repository}/actions"
    if environment["GITHUB_EVENT_NAME"] == "workflow_run":
        run_id = event["workflow_run"]["id"]
        if not isinstance(run_id, int) or run_id <= 0:
            raise ValueError("Invalid CI run")
        candidates = [fetch(f"{prefix}/runs/{run_id}")]
    elif environment["GITHUB_EVENT_NAME"] == "workflow_dispatch" and environment.get("GITHUB_REF") == "refs/heads/main":
        candidates = fetch(f"{prefix}/workflows/android-ci.yml/runs?head_sha={commit}&branch=main&event=push&status=completed&per_page=30")["workflow_runs"]
    else:
        raise ValueError("Unsupported publication event")
    for run in candidates:
        if run.get("head_sha") == commit and run.get("conclusion") == "success":
            jobs = fetch(f"{prefix}/runs/{run['id']}/jobs?per_page=100")["jobs"]
            require_verified_main_run(run, jobs, repository, commit)
            return run["id"]
    raise ValueError("No successful exact product CI found")
