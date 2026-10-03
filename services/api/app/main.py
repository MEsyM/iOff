import hashlib, secrets, uuid
from datetime import datetime, timedelta, timezone
from typing import Any
import jwt
from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, EmailStr, Field
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy import create_engine, String, DateTime, ForeignKey, Integer, Boolean, JSON, Text, UniqueConstraint, select
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker, Session

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    database_url: str
    jwt_secret: str
    magic_link_pepper: str
    magic_link_base_url: str = "lonerider://auth"
    auth_email_mode: str = "console"
    access_token_minutes: int = 60
    refresh_token_days: int = 30
    magic_link_minutes: int = 15

settings = Settings()
engine = create_engine(settings.database_url, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)

class Base(DeclarativeBase): pass
def utcnow(): return datetime.now(timezone.utc)

class User(Base):
    __tablename__="users"
    id: Mapped[uuid.UUID]=mapped_column(primary_key=True,default=uuid.uuid4)
    email: Mapped[str]=mapped_column(String(320),unique=True,index=True)
    status: Mapped[str]=mapped_column(String(20),default="active")
    created_at: Mapped[datetime]=mapped_column(DateTime(timezone=True),default=utcnow)
    last_login_at: Mapped[datetime|None]=mapped_column(DateTime(timezone=True))

class MagicLink(Base):
    __tablename__="magic_links"
    id: Mapped[uuid.UUID]=mapped_column(primary_key=True,default=uuid.uuid4)
    user_id: Mapped[uuid.UUID]=mapped_column(ForeignKey("users.id",ondelete="CASCADE"),index=True)
    token_hash: Mapped[str]=mapped_column(String(64),unique=True,index=True)
    expires_at: Mapped[datetime]=mapped_column(DateTime(timezone=True))
    used_at: Mapped[datetime|None]=mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime]=mapped_column(DateTime(timezone=True),default=utcnow)

class RefreshToken(Base):
    __tablename__="refresh_tokens"
    id: Mapped[uuid.UUID]=mapped_column(primary_key=True,default=uuid.uuid4)
    user_id: Mapped[uuid.UUID]=mapped_column(ForeignKey("users.id",ondelete="CASCADE"),index=True)
    token_hash: Mapped[str]=mapped_column(String(64),unique=True,index=True)
    expires_at: Mapped[datetime]=mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime|None]=mapped_column(DateTime(timezone=True))

class Profile(Base):
    __tablename__="profiles"
    id: Mapped[uuid.UUID]=mapped_column(primary_key=True,default=uuid.uuid4)
    user_id: Mapped[uuid.UUID]=mapped_column(ForeignKey("users.id",ondelete="CASCADE"),index=True)
    name: Mapped[str]=mapped_column(String(80))
    language: Mapped[str]=mapped_column(String(10),default="en")
    age_group: Mapped[str|None]=mapped_column(String(30))
    settings_json: Mapped[dict]=mapped_column(JSON,default=dict)
    created_at: Mapped[datetime]=mapped_column(DateTime(timezone=True),default=utcnow)

class Device(Base):
    __tablename__="devices"
    __table_args__=(UniqueConstraint("user_id","installation_id"),)
    id: Mapped[uuid.UUID]=mapped_column(primary_key=True,default=uuid.uuid4)
    user_id: Mapped[uuid.UUID]=mapped_column(ForeignKey("users.id",ondelete="CASCADE"),index=True)
    installation_id: Mapped[str]=mapped_column(String(100))
    platform: Mapped[str]=mapped_column(String(20))
    device_name: Mapped[str|None]=mapped_column(String(120))
    app_version: Mapped[str|None]=mapped_column(String(40))
    last_seen_at: Mapped[datetime]=mapped_column(DateTime(timezone=True),default=utcnow)

class GameEvent(Base):
    __tablename__="game_events"
    event_id: Mapped[uuid.UUID]=mapped_column(primary_key=True)
    profile_id: Mapped[uuid.UUID]=mapped_column(ForeignKey("profiles.id",ondelete="CASCADE"),index=True)
    game: Mapped[str]=mapped_column(String(50),index=True)
    event_type: Mapped[str]=mapped_column(String(60))
    question_id: Mapped[str|None]=mapped_column(String(100))
    xp_delta: Mapped[int]=mapped_column(Integer,default=0)
    correct: Mapped[bool|None]=mapped_column(Boolean)
    occurred_at: Mapped[datetime]=mapped_column(DateTime(timezone=True),index=True)
    payload: Mapped[dict]=mapped_column(JSON,default=dict)
    received_at: Mapped[datetime]=mapped_column(DateTime(timezone=True),default=utcnow)

class Progress(Base):
    __tablename__="progress"
    __table_args__=(UniqueConstraint("profile_id","game"),)
    id: Mapped[uuid.UUID]=mapped_column(primary_key=True,default=uuid.uuid4)
    profile_id: Mapped[uuid.UUID]=mapped_column(ForeignKey("profiles.id",ondelete="CASCADE"),index=True)
    game: Mapped[str]=mapped_column(String(50))
    xp: Mapped[int]=mapped_column(Integer,default=0)
    level: Mapped[int]=mapped_column(Integer,default=1)
    current_streak: Mapped[int]=mapped_column(Integer,default=0)
    best_streak: Mapped[int]=mapped_column(Integer,default=0)
    total_answered: Mapped[int]=mapped_column(Integer,default=0)
    total_correct: Mapped[int]=mapped_column(Integer,default=0)
    updated_at: Mapped[datetime]=mapped_column(DateTime(timezone=True),default=utcnow)

def db_session():
    db=SessionLocal()
    try: yield db
    finally: db.close()

def h(raw:str): return hashlib.sha256((raw+settings.magic_link_pepper).encode()).hexdigest()
def opaque(): return secrets.token_urlsafe(48)
def issue_access(uid:uuid.UUID):
    now=utcnow(); exp=now+timedelta(minutes=settings.access_token_minutes)
    return jwt.encode({"sub":str(uid),"iat":int(now.timestamp()),"exp":int(exp.timestamp()),"type":"access"},settings.jwt_secret,algorithm="HS256")
def issue_refresh(db:Session,uid:uuid.UUID):
    raw=opaque(); db.add(RefreshToken(user_id=uid,token_hash=h(raw),expires_at=utcnow()+timedelta(days=settings.refresh_token_days))); return raw

bearer=HTTPBearer(auto_error=False)
def current_user(c:HTTPAuthorizationCredentials|None=Depends(bearer),db:Session=Depends(db_session)):
    if not c: raise HTTPException(401,"Missing token")
    try:
        p=jwt.decode(c.credentials,settings.jwt_secret,algorithms=["HS256"])
        if p.get("type")!="access": raise ValueError()
        uid=uuid.UUID(p["sub"])
    except Exception: raise HTTPException(401,"Invalid token")
    u=db.get(User,uid)
    if not u or u.status!="active": raise HTTPException(401,"User unavailable")
    return u

class EmailIn(BaseModel): email: EmailStr
class TokenIn(BaseModel): token:str
class RefreshIn(BaseModel): refresh_token:str
class ProfileIn(BaseModel):
    name:str=Field(min_length=1,max_length=80)
    language:str="en"
    age_group:str|None=None
    settings:dict[str,Any]=Field(default_factory=dict)
class DeviceIn(BaseModel):
    installation_id:str
    platform:str=Field(pattern="^(ios|android)$")
    device_name:str|None=None
    app_version:str|None=None
class EventIn(BaseModel):
    event_id:uuid.UUID
    profile_id:uuid.UUID
    game:str
    event_type:str
    question_id:str|None=None
    xp_delta:int=0
    correct:bool|None=None
    occurred_at:datetime
    payload:dict[str,Any]=Field(default_factory=dict)
class EventsIn(BaseModel): events:list[EventIn]=Field(max_length=500)

app=FastAPI(title="Lone Rider API",version="0.1.0")

@app.get("/health")
def health(): return {"status":"ok","service":"lone-rider-api","version":"0.1.0"}

@app.post("/v1/auth/magic-link/request",status_code=202)
def request_link(body:EmailIn,db:Session=Depends(db_session)):
    email=body.email.lower().strip()
    u=db.scalar(select(User).where(User.email==email))
    if not u: u=User(email=email); db.add(u); db.flush()
    raw=opaque(); db.add(MagicLink(user_id=u.id,token_hash=h(raw),expires_at=utcnow()+timedelta(minutes=settings.magic_link_minutes)))
    db.commit()
    link=settings.magic_link_base_url+"?token="+raw
    if settings.auth_email_mode=="console": print("[LONE_RIDER_MAGIC_LINK]",email,link)
    return {"accepted":True,"delivery":settings.auth_email_mode}

@app.post("/v1/auth/magic-link/verify")
def verify_link(body:TokenIn,db:Session=Depends(db_session)):
    row=db.scalar(select(MagicLink).where(MagicLink.token_hash==h(body.token)))
    if not row or row.used_at or row.expires_at<=utcnow(): raise HTTPException(401,"Invalid or expired link")
    u=db.get(User,row.user_id)
    row.used_at=utcnow(); u.last_login_at=utcnow()
    access=issue_access(u.id); refresh=issue_refresh(db,u.id); db.commit()
    return {"access_token":access,"refresh_token":refresh,"token_type":"bearer","expires_in":settings.access_token_minutes*60}

@app.post("/v1/auth/refresh")
def refresh(body:RefreshIn,db:Session=Depends(db_session)):
    row=db.scalar(select(RefreshToken).where(RefreshToken.token_hash==h(body.refresh_token)))
    if not row or row.revoked_at or row.expires_at<=utcnow(): raise HTTPException(401,"Invalid refresh token")
    row.revoked_at=utcnow(); access=issue_access(row.user_id); refresh_raw=issue_refresh(db,row.user_id); db.commit()
    return {"access_token":access,"refresh_token":refresh_raw,"token_type":"bearer","expires_in":settings.access_token_minutes*60}

@app.get("/v1/me")
def me(u:User=Depends(current_user)):
    return {"id":str(u.id),"email":u.email,"status":u.status,"created_at":u.created_at,"last_login_at":u.last_login_at}

@app.get("/v1/profiles")
def profiles(u:User=Depends(current_user),db:Session=Depends(db_session)):
    rows=db.scalars(select(Profile).where(Profile.user_id==u.id).order_by(Profile.created_at)).all()
    return [{"id":str(x.id),"name":x.name,"language":x.language,"age_group":x.age_group,"settings":x.settings_json} for x in rows]

@app.post("/v1/profiles",status_code=201)
def create_profile(body:ProfileIn,u:User=Depends(current_user),db:Session=Depends(db_session)):
    p=Profile(user_id=u.id,name=body.name,language=body.language,age_group=body.age_group,settings_json=body.settings)
    db.add(p); db.commit(); db.refresh(p); return {"id":str(p.id),"name":p.name}

@app.put("/v1/devices/current")
def device(body:DeviceIn,u:User=Depends(current_user),db:Session=Depends(db_session)):
    d=db.scalar(select(Device).where(Device.user_id==u.id,Device.installation_id==body.installation_id))
    if not d: d=Device(user_id=u.id,**body.model_dump()); db.add(d)
    else:
        d.platform=body.platform; d.device_name=body.device_name; d.app_version=body.app_version; d.last_seen_at=utcnow()
    db.commit(); db.refresh(d); return {"id":str(d.id),"ok":True}

def recompute(db:Session,pid:uuid.UUID,game:str):
    ev=db.scalars(select(GameEvent).where(GameEvent.profile_id==pid,GameEvent.game==game).order_by(GameEvent.occurred_at,GameEvent.received_at)).all()
    prog=db.scalar(select(Progress).where(Progress.profile_id==pid,Progress.game==game))
    if not prog: prog=Progress(profile_id=pid,game=game); db.add(prog)
    xp=sum(x.xp_delta for x in ev); answered=correct=streak=best=0
    for x in ev:
        if x.correct is None: continue
        answered+=1
        if x.correct: correct+=1; streak+=1; best=max(best,streak)
        else: streak=0
    prog.xp=max(0,xp); prog.level=max(1,prog.xp//500+1); prog.current_streak=streak; prog.best_streak=best
    prog.total_answered=answered; prog.total_correct=correct; prog.updated_at=utcnow()

@app.post("/v1/sync/events")
def sync(body:EventsIn,u:User=Depends(current_user),db:Session=Depends(db_session)):
    pids={e.profile_id for e in body.events}
    owned=set(db.scalars(select(Profile.id).where(Profile.user_id==u.id,Profile.id.in_(pids))).all()) if pids else set()
    if owned!=pids: raise HTTPException(403,"Profile ownership mismatch")
    accepted=duplicates=0; touched=set()
    for e in body.events:
        if db.get(GameEvent,e.event_id): duplicates+=1; continue
        db.add(GameEvent(**e.model_dump())); db.flush(); accepted+=1; touched.add((e.profile_id,e.game))
    for pid,game in touched: recompute(db,pid,game)
    db.commit(); return {"accepted":accepted,"duplicates":duplicates}

@app.get("/v1/sync/progress/{profile_id}")
def progress(profile_id:uuid.UUID,u:User=Depends(current_user),db:Session=Depends(db_session)):
    p=db.get(Profile,profile_id)
    if not p or p.user_id!=u.id: raise HTTPException(404,"Profile not found")
    rows=db.scalars(select(Progress).where(Progress.profile_id==profile_id).order_by(Progress.game)).all()
    return [{"game":x.game,"xp":x.xp,"level":x.level,"current_streak":x.current_streak,"best_streak":x.best_streak,
             "total_answered":x.total_answered,"total_correct":x.total_correct,"updated_at":x.updated_at} for x in rows]


# Secure admin authentication + content admin / public live-content API
from app.admin_auth import router as admin_auth_router
from app.admin_content import router as content_admin_router, seed_content_if_empty
app.include_router(admin_auth_router)
app.include_router(content_admin_router)

@app.on_event("startup")
def seed_initial_content():
    seed_content_if_empty()
