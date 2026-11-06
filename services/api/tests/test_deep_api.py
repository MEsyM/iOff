import os
import uuid

from fastapi.testclient import TestClient

from app.main import Base, engine


def reset_db():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)


def auth_headers(token: str):
    return {"Authorization": f"Bearer {token}"}


def test_full_user_profile_progress_and_admin_password_flow():
    reset_db()
    from app.main import app

    with TestClient(app) as client:
        email = f"ci-{uuid.uuid4().hex[:10]}@example.test"
        old_password = f"old-{uuid.uuid4().hex}"
        new_password = f"new-{uuid.uuid4().hex}"

        signup = client.post("/v1/auth/signup", json={"email": email, "password": old_password})
        assert signup.status_code == 201, signup.text
        access = signup.json()["access_token"]

        me = client.get("/v1/me", headers=auth_headers(access))
        assert me.status_code == 200
        assert me.json()["email"] == email

        profile = client.post(
            "/v1/profiles",
            headers=auth_headers(access),
            json={"name": "CI Rider", "language": "en", "age_group": "adult", "settings": {"voice": True}},
        )
        assert profile.status_code == 201, profile.text
        profile_id = profile.json()["id"]

        device = client.put(
            "/v1/devices/current",
            headers=auth_headers(access),
            json={
                "installation_id": f"ci-{uuid.uuid4()}",
                "platform": "android",
                "device_name": "GitHub Emulator",
                "app_version": "ci",
            },
        )
        assert device.status_code == 200, device.text
        assert device.json()["ok"] is True

        event_id = str(uuid.uuid4())
        sync_payload = {
            "events": [{
                "event_id": event_id,
                "profile_id": profile_id,
                "game": "quick_trivia",
                "event_type": "progress_snapshot",
                "question_id": None,
                "xp_delta": 1250,
                "correct": None,
                "occurred_at": "2026-01-01T00:00:00Z",
                "payload": {
                    "total_answered": 15,
                    "total_correct": 12,
                    "current_streak": 3,
                    "best_streak": 7,
                },
            }]
        }

        sync = client.post("/v1/sync/events", headers=auth_headers(access), json=sync_payload)
        assert sync.status_code == 200, sync.text
        assert sync.json() == {"accepted": 1, "duplicates": 0}

        duplicate = client.post("/v1/sync/events", headers=auth_headers(access), json=sync_payload)
        assert duplicate.status_code == 200
        assert duplicate.json() == {"accepted": 0, "duplicates": 1}

        progress = client.get(f"/v1/sync/progress/{profile_id}", headers=auth_headers(access))
        assert progress.status_code == 200
        row = progress.json()[0]
        assert row["game"] == "quick_trivia"
        assert row["xp"] == 1250
        assert row["level"] == 3
        assert row["total_answered"] == 15
        assert row["total_correct"] == 12
        assert row["best_streak"] == 7

        admin_login = client.post(
            "/v1/admin/auth/login",
            json={
                "login": os.environ["TEST_ADMIN_LOGIN"],
                "password": os.environ["TEST_ADMIN_PASSWORD"],
            },
        )
        assert admin_login.status_code == 200, admin_login.text
        csrf = admin_login.json()["csrf_token"]

        users = client.get("/v1/admin/auth/app-users")
        assert users.status_code == 200, users.text
        target = next(x for x in users.json()["items"] if x["email"] == email)
        assert target["profile_count"] == 1
        assert target["device_count"] == 1
        assert target["has_password"] is True

        detail = client.get(f"/v1/admin/auth/app-users/{target['id']}")
        assert detail.status_code == 200, detail.text
        assert detail.json()["profiles"][0]["progress"][0]["xp"] == 1250

        changed = client.patch(
            f"/v1/admin/auth/app-users/{target['id']}/password",
            headers={"X-CSRF-Token": csrf},
            json={"new_password": new_password},
        )
        assert changed.status_code == 204, changed.text

        old_login = client.post("/v1/auth/login", json={"email": email, "password": old_password})
        assert old_login.status_code == 401

        new_login = client.post("/v1/auth/login", json={"email": email, "password": new_password})
        assert new_login.status_code == 200, new_login.text


def test_live_content_manifest_and_each_published_kind_are_readable():
    reset_db()
    from app.main import app

    with TestClient(app) as client:
        manifest = client.get("/v1/content/manifest")
        assert manifest.status_code == 200, manifest.text
        payload = manifest.json()
        assert payload["contentVersion"]
        assert payload["games"]

        for kind, meta in payload["games"].items():
            assert meta["count"] > 0
            response = client.get(f"/v1/content/{kind}")
            assert response.status_code == 200, f"{kind}: {response.text}"
            body = response.json()
            assert body["contentVersion"] == payload["contentVersion"]
            assert len(body["items"]) == meta["count"]
