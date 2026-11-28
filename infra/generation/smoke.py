"""Drive a deterministic generation fixture through the real Core, worker, Rusta and RustFS."""

import hashlib
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

from fixture import CREATE, DENY, EDIT

API = os.environ.get("PLAYLY_SMOKE_API", "http://127.0.0.1:8080/api/v1")


def call(path, method="GET", data=None, token=None, key=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    with urllib.request.urlopen(urllib.request.Request(
        API + path, None if data is None else json.dumps(data).encode(), headers, method=method,
    ), timeout=20) as response:
        return json.load(response)


def expect_error(status, path):
    try:
        call(path)
    except urllib.error.HTTPError as error:
        assert error.code == status, error.code
    else:
        raise AssertionError(f"Expected status {status} for {path}")


def generation(game, token, prompt, base=None):
    key = str(uuid.uuid4())
    path = f"/games/{game}/generations"
    data = {"prompt": prompt}
    if base:
        data["baseVersionId"] = base
    accepted = call(path, "POST", data, token, key)
    assert call(path, "POST", data, token, key)["id"] == accepted["id"]
    for _ in range(100):
        result = call(f"/generations/{accepted['id']}", token=token)
        if result["status"] in {"SUCCEEDED", "FAILED"}:
            return result
        time.sleep(1)
    raise AssertionError("Generation timed out")


def package(game, version, token=None):
    result = call(f"/games/{game}/versions/{version}/package", token=token)
    assert result["gameSpecVersion"] == "2.0"
    assert result["runtimeVersion"] == "2.0.0"
    assert not ({"sourceArchive", "validationReport", "reportSignature"} & result.keys())
    paths = set()
    for entry in [result["gameSpec"], *result["assets"]]:
        assert not ({"bucket", "key"} & entry.keys())
        url = entry["url"]
        parsed = urllib.parse.urlsplit(url)
        assert parsed._replace(query="").geturl() == API + "/game-files"
        with urllib.request.urlopen(url, timeout=20) as response:
            assert response.headers.get_content_type() == "application/octet-stream"
            assert response.headers["Content-Disposition"] == "attachment"
            assert response.headers["Cache-Control"] == "no-store"
            body = response.read()
        assert hashlib.sha256(body).hexdigest() == entry["sha256"]
        assert len(body) == entry["sizeBytes"] and entry["path"] not in paths
        paths.add(entry["path"])
    assert {"game.json", "index.html"} <= paths
    return result


def main():
    token = call("/auth/register", "POST", {
        "email": f"fixture-{uuid.uuid4()}@example.com", "password": "playly-fixture-password",
        "displayName": "Fixture",
    })["accessToken"]
    title = "PlayLy Smoke " + str(uuid.uuid4())[:8]
    game = call("/games", "POST", {"title": title, "description": ""}, token)["id"]
    first = generation(game, token, CREATE)
    assert first["status"] == "SUCCEEDED", first
    first_version = first["resultVersionId"]
    expect_error(404, f"/games/{game}/versions/{first_version}/package")
    old = package(game, first_version, token)
    query = urllib.parse.urlsplit(old["gameSpec"]["url"]).query
    expect_error(404, "/game-files")
    expect_error(404, "/game-files?" + query.replace("token=", "token=0", 1))
    second = generation(game, token, EDIT, first_version)
    assert second["status"] == "SUCCEEDED", second
    second_version = second["resultVersionId"]
    assert first_version != second_version
    new = package(game, second_version, token)
    assert {file["sha256"] for file in old["assets"]} != {file["sha256"] for file in new["assets"]}
    denied = generation(game, token, DENY)
    assert denied["status"] == "FAILED" and denied["error"]["code"] == "UNSUPPORTED_GAME_REQUEST", denied
    versions = call(f"/games/{game}/versions", token=token)["items"]
    assert [item["id"] for item in versions] == [second_version, first_version]
    call(f"/games/{game}/publication", "PUT", {"versionId": second_version}, token)
    public = package(game, second_version)
    assert public["versionId"] == second_version
    expect_error(404, f"/games/{game}/versions/{first_version}/package")
    print(f"Published and verified {title} ({game})")


if __name__ == "__main__":
    main()
