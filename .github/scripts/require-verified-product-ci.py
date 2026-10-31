"""Fail closed before making a product release; never echo credentials or API responses."""
import json
import os
from pathlib import Path
import urllib.request
from verified_product_ci import verify_current_event


def fetch(path):
    request = urllib.request.Request("https://api.github.com" + path, headers={
        "Authorization": "Bearer " + os.environ["GITHUB_TOKEN"],
        "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


if __name__ == "__main__":
    try:
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        run = verify_current_event(os.environ, event, fetch)
        print(f"Verified complete product CI run {run}")
    except (OSError, ValueError, KeyError, TypeError):
        raise SystemExit("Product publication blocked: exact complete main CI not verified")
