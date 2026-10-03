import base64
import hashlib
import hmac
import os
import secrets
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Cookie, Depends, Header, HTTPException, Response, status
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import Boolean, DateTime, ForeignKey, String, func, select
from sqlalchemy.orm import Mapped, mapped_column

from app.main import Base, SessionLocal

def utcnow():
    return datetime.now(timezone.utc)

SESSION_COOKIE = "lr_admin_session"
SESSION_HOURS = int(os.getenv("ADMIN_SESSION_HOURS","12"))
SETUP_CODE = os.getenv("ADMIN_SETUP_CODE","")
COOKIE_SECURE = os.getenv("ADMIN_COOKIE_SECURE","true").lower() != "false"

ROLE_RANK = {"viewer": 10, "editor": 20, "admin": 30}

class AdminUser(Base):
    __tablename__ = "admin_users"
    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    display_name: Mapped[str] = mapped_column(String(120))
    password_hash: Mapped[str] = mapped_column(String(300))
    role: Mapped[str] = mapped_column(String(20), default="viewer", index=True)
    active: Mapped[bool] = mapped_column(Boolean, default=True, index=True)
    failed_login_count: Mapped[int] = mapped_column(default=0)
    locked_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)
    last_login_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

class AdminSession(Base):
    __tablename__ = "admin_sessions"
    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    admin_user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("admin_users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    csrf_token: Mapped[str] = mapped_column(String(100))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

class LoginIn(BaseModel):
    login: str = Field(min_length=1, max_length=320)
    password: str = Field(min_length=1, max_length=256)

class SetupIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=12, max_length=256)
    setup_code: str = Field(min_length=8, max_length=256)
    display_name: str = Field(min_length=1, max_length=120)

class AdminCreateIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=12, max_length=256)
    display_name: str = Field(min_length=1, max_length=120)
    role: str = Field(pattern="^(viewer|editor|admin)$")

class AdminPatchIn(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=120)
    role: str | None = Field(default=None, pattern="^(viewer|editor|admin)$")
    active: bool | None = None
    password: str | None = Field(default=None, min_length=12, max_length=256)

class PasswordChangeIn(BaseModel):
    current_password: str = Field(min_length=12, max_length=256)
    new_password: str = Field(min_length=12, max_length=256)

class AdminPrincipal:
    def __init__(self, user: AdminUser, session: AdminSession):
        self.user = user
        self.session = session

def _b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")

def _unb64(raw: str) -> bytes:
    return base64.urlsafe_b64decode(raw + "=" * (-len(raw) % 4))

def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(password.encode(), salt=salt, n=2**15, r=8, p=1, dklen=32)
    return "scrypt-v1$" + _b64(salt) + "$" + _b64(digest)

def verify_password(password: str, encoded: str) -> bool:
    try:
        version, salt_b64, digest_b64 = encoded.split("$", 2)
        if version != "scrypt-v1":
            return False
        digest = hashlib.scrypt(password.encode(), salt=_unb64(salt_b64), n=2**15, r=8, p=1, dklen=32)
        return hmac.compare_digest(digest, _unb64(digest_b64))
    except Exception:
        return False

def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()

def serialize_user(user: AdminUser):
    return {
        "id": str(user.id), "email": user.email, "display_name": user.display_name,
        "role": user.role, "active": user.active, "created_at": user.created_at,
        "last_login_at": user.last_login_at,
    }

def set_session_cookie(response: Response, token: str):
    response.set_cookie(
        SESSION_COOKIE, token, max_age=SESSION_HOURS * 3600, httponly=True,
        secure=COOKIE_SECURE, samesite="strict", path="/"
    )

def clear_session_cookie(response: Response):
    response.delete_cookie(SESSION_COOKIE, path="/", secure=COOKIE_SECURE, samesite="strict")

def resolve_principal(session_token: str | None = Cookie(default=None, alias=SESSION_COOKIE)) -> AdminPrincipal:
    if not session_token:
        raise HTTPException(status_code=401, detail="Admin login required")
    with SessionLocal() as db:
        sess = db.scalar(select(AdminSession).where(AdminSession.token_hash == hash_token(session_token)))
        now = utcnow()
        if not sess or sess.revoked_at is not None or sess.expires_at <= now:
            raise HTTPException(status_code=401, detail="Admin session expired")
        user = db.get(AdminUser, sess.admin_user_id)
        if not user or not user.active:
            raise HTTPException(status_code=401, detail="Admin account unavailable")
        sess.last_seen_at = now
        db.commit()
        db.expunge(user); db.expunge(sess)
        return AdminPrincipal(user, sess)

def require_role(min_role: str):
    def dep(principal: AdminPrincipal = Depends(resolve_principal)):
        if ROLE_RANK.get(principal.user.role, 0) < ROLE_RANK[min_role]:
            raise HTTPException(status_code=403, detail="Insufficient admin role")
        return principal
    return dep

require_viewer = require_role("viewer")
require_editor = require_role("editor")
require_admin = require_role("admin")

def csrf_protect(
    principal: AdminPrincipal = Depends(resolve_principal),
    csrf: str | None = Header(default=None, alias="X-CSRF-Token"),
):
    if not csrf or not hmac.compare_digest(csrf, principal.session.csrf_token):
        raise HTTPException(status_code=403, detail="Invalid CSRF token")
    return principal

def require_editor_write(
    principal: AdminPrincipal = Depends(require_editor),
    csrf_principal: AdminPrincipal = Depends(csrf_protect),
):
    return principal

def require_admin_write(
    principal: AdminPrincipal = Depends(require_admin),
    csrf_principal: AdminPrincipal = Depends(csrf_protect),
):
    return principal

router = APIRouter(prefix="/v1/admin/auth", tags=["admin-auth"])

@router.get("/status")
def auth_status():
    test_enabled = os.getenv("TEST_ADMIN_ENABLED","false").lower() == "true"
    with SessionLocal() as db:
        count = db.scalar(select(func.count()).select_from(AdminUser))
        return {"setup_required": (count == 0 and not test_enabled), "test_admin_enabled": test_enabled}

@router.post("/setup")
def setup(body: SetupIn, response: Response):
    with SessionLocal() as db:
        if db.scalar(select(func.count()).select_from(AdminUser)) > 0:
            raise HTTPException(status_code=409, detail="Admin setup already completed")
        if not SETUP_CODE or not secrets.compare_digest(body.setup_code, SETUP_CODE):
            raise HTTPException(status_code=403, detail="Invalid one-time setup code")
        user = AdminUser(
            email=body.email.lower().strip(), display_name=body.display_name.strip(),
            password_hash=hash_password(body.password), role="admin", active=True,
        )
        db.add(user); db.flush()
        raw = secrets.token_urlsafe(48)
        csrf = secrets.token_urlsafe(32)
        sess = AdminSession(
            admin_user_id=user.id, token_hash=hash_token(raw), csrf_token=csrf,
            expires_at=utcnow() + timedelta(hours=SESSION_HOURS),
        )
        db.add(sess); db.commit(); db.refresh(user)
        set_session_cookie(response, raw)
        return {"user": serialize_user(user), "csrf_token": csrf}

@router.post("/login")
def login(body: LoginIn, response: Response):
    with SessionLocal() as db:
        login_value = body.login.lower().strip()
        user = db.scalar(select(AdminUser).where(AdminUser.email == login_value))
        if user is None and os.getenv("TEST_ADMIN_ENABLED","false").lower() == "true":
            test_login = os.getenv("TEST_ADMIN_LOGIN","").lower().strip()
            test_password = os.getenv("TEST_ADMIN_PASSWORD","")
            if test_login and test_password and secrets.compare_digest(login_value, test_login):
                user = db.scalar(select(AdminUser).where(AdminUser.email == "test-admin@local.invalid"))
                if user is None:
                    user = AdminUser(
                        email="test-admin@local.invalid",
                        display_name="Test Admin",
                        password_hash=hash_password(test_password),
                        role="admin",
                        active=True,
                    )
                    db.add(user)
                    db.flush()
                elif not verify_password(test_password, user.password_hash):
                    user.password_hash = hash_password(test_password)
                db.commit()
        now = utcnow()
        if not user:
            raise HTTPException(status_code=401, detail="Invalid email or password")
        if user.locked_until and user.locked_until > now:
            raise HTTPException(status_code=429, detail="Account temporarily locked")
        if not user.active or not verify_password(body.password, user.password_hash):
            user.failed_login_count += 1
            if user.failed_login_count >= 5:
                user.locked_until = now + timedelta(minutes=15)
                user.failed_login_count = 0
            db.commit()
            raise HTTPException(status_code=401, detail="Invalid email or password")
        user.failed_login_count = 0; user.locked_until = None; user.last_login_at = now
        raw = secrets.token_urlsafe(48); csrf = secrets.token_urlsafe(32)
        sess = AdminSession(
            admin_user_id=user.id, token_hash=hash_token(raw), csrf_token=csrf,
            expires_at=now + timedelta(hours=SESSION_HOURS),
        )
        db.add(sess); db.commit(); db.refresh(user)
        set_session_cookie(response, raw)
        return {"user": serialize_user(user), "csrf_token": csrf}

@router.get("/me")
def me(principal: AdminPrincipal = Depends(require_viewer)):
    return {"user": serialize_user(principal.user), "csrf_token": principal.session.csrf_token}

@router.post("/logout", status_code=204)
def logout(response: Response, principal: AdminPrincipal = Depends(csrf_protect)):
    with SessionLocal() as db:
        sess = db.get(AdminSession, principal.session.id)
        if sess: sess.revoked_at = utcnow(); db.commit()
    clear_session_cookie(response)

@router.post("/change-password", status_code=204)
def change_password(body: PasswordChangeIn, principal: AdminPrincipal = Depends(csrf_protect)):
    with SessionLocal() as db:
        user = db.get(AdminUser, principal.user.id)
        if not user or not verify_password(body.current_password, user.password_hash):
            raise HTTPException(status_code=401, detail="Current password is incorrect")
        user.password_hash = hash_password(body.new_password)
        db.query(AdminSession).filter(AdminSession.admin_user_id == user.id, AdminSession.id != principal.session.id).update({"revoked_at": utcnow()})
        db.commit()

@router.get("/users")
def list_admin_users(principal: AdminPrincipal = Depends(require_admin)):
    with SessionLocal() as db:
        rows = db.scalars(select(AdminUser).order_by(AdminUser.email)).all()
        return [serialize_user(x) for x in rows]

@router.post("/users", status_code=201)
def create_admin_user(body: AdminCreateIn, principal: AdminPrincipal = Depends(require_admin_write)):
    with SessionLocal() as db:
        email = body.email.lower().strip()
        if db.scalar(select(AdminUser).where(AdminUser.email == email)):
            raise HTTPException(status_code=409, detail="Admin email already exists")
        user = AdminUser(email=email, display_name=body.display_name.strip(), password_hash=hash_password(body.password), role=body.role)
        db.add(user); db.commit(); db.refresh(user)
        return serialize_user(user)

@router.patch("/users/{user_id}")
def patch_admin_user(user_id: uuid.UUID, body: AdminPatchIn, principal: AdminPrincipal = Depends(require_admin_write)):
    with SessionLocal() as db:
        user = db.get(AdminUser, user_id)
        if not user: raise HTTPException(status_code=404, detail="Admin user not found")
        if user.id == principal.user.id and body.active is False:
            raise HTTPException(status_code=400, detail="You cannot disable your own account")
        if user.id == principal.user.id and body.role and body.role != "admin":
            raise HTTPException(status_code=400, detail="You cannot demote your own admin role")
        changes = body.model_dump(exclude_unset=True)
        password = changes.pop("password", None)
        for k,v in changes.items(): setattr(user,k,v)
        if password: user.password_hash = hash_password(password)
        user.updated_at = utcnow()
        if password or body.active is False:
            db.query(AdminSession).filter(AdminSession.admin_user_id == user.id).update({"revoked_at": utcnow()})
        db.commit(); db.refresh(user); return serialize_user(user)
