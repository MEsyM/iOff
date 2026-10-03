import json
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field
from sqlalchemy import Boolean, DateTime, Integer, JSON, String, UniqueConstraint, func, select
from sqlalchemy.orm import Mapped, mapped_column

from app.main import Base, SessionLocal
from app.admin_auth import AdminPrincipal, require_viewer, require_editor_write, require_admin_write

def utcnow():
    return datetime.now(timezone.utc)

class ContentItem(Base):
    __tablename__ = "content_items"
    __table_args__ = (UniqueConstraint("kind","public_id",name="uq_content_kind_public_id"),)
    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    public_id: Mapped[str] = mapped_column(String(120), index=True)
    kind: Mapped[str] = mapped_column(String(40), index=True)
    category: Mapped[str | None] = mapped_column(String(80), index=True)
    difficulty: Mapped[int | None] = mapped_column(Integer, index=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True, index=True)
    status: Mapped[str] = mapped_column(String(20), default="draft", index=True)
    data: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)

class ContentRelease(Base):
    __tablename__ = "content_releases"
    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    version: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    note: Mapped[str | None] = mapped_column(String(300))
    active: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    item_count: Mapped[int] = mapped_column(Integer, default=0)
    snapshot: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    published_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

class ContentAudit(Base):
    __tablename__ = "content_audit"
    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    action: Mapped[str] = mapped_column(String(60), index=True)
    item_kind: Mapped[str | None] = mapped_column(String(40))
    item_public_id: Mapped[str | None] = mapped_column(String(120))
    data: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, index=True)

class ItemIn(BaseModel):
    public_id: str = Field(min_length=1,max_length=120)
    kind: str = Field(min_length=1,max_length=40)
    category: str | None = None
    difficulty: int | None = None
    enabled: bool = True
    data: dict[str,Any] = Field(default_factory=dict)

class ItemPatch(BaseModel):
    public_id: str | None = None
    category: str | None = None
    difficulty: int | None = None
    enabled: bool | None = None
    status: str | None = None
    data: dict[str,Any] | None = None

class PublishIn(BaseModel):
    note: str | None = None

router = APIRouter()
def audit(db, action, item=None, data=None, principal=None):
    payload = dict(data or {})
    if principal is not None:
        payload["actor_admin_id"] = str(principal.user.id)
        payload["actor_email"] = principal.user.email
    db.add(ContentAudit(action=action,item_kind=getattr(item,"kind",None),item_public_id=getattr(item,"public_id",None),data=payload))

def item_json(x):
    return {"id":str(x.id),"public_id":x.public_id,"kind":x.kind,"category":x.category,"difficulty":x.difficulty,
            "enabled":x.enabled,"status":x.status,"data":x.data,"created_at":x.created_at,"updated_at":x.updated_at}

def seed_content_if_empty():
    with SessionLocal() as db:
        payload=json.loads(Path(__file__).with_name("seed_content.json").read_text(encoding="utf-8"))
        existing=set(db.execute(select(ContentItem.kind, ContentItem.public_id)).all())
        added=0
        for row in payload["items"]:
            key=(row["kind"], row["public_id"])
            if key in existing:
                continue
            db.add(ContentItem(public_id=row["public_id"],kind=row["kind"],category=row.get("category"),
                               difficulty=row.get("difficulty"),enabled=row.get("enabled",True),status="draft",data=row["data"]))
            existing.add(key)
            added += 1
        if added:
            audit(db,"seed_missing_content",data={"count":added})
            db.commit()

            active=db.scalar(select(ContentRelease).where(ContentRelease.active==True).order_by(ContentRelease.published_at.desc()))
            active_games=(active.snapshot or {}).get("games", {}) if active else {}
            if "kids_game" not in active_games:
                rows=db.scalars(
                    select(ContentItem)
                    .where(ContentItem.enabled==True, ContentItem.status!="archived")
                    .order_by(ContentItem.kind, ContentItem.public_id)
                ).all()
                today=datetime.now(timezone.utc).strftime("%Y.%m.%d")
                existing_count=db.scalar(
                    select(func.count()).select_from(ContentRelease).where(ContentRelease.version.like(today+".%"))
                )
                version=f"{today}.{existing_count+1}"
                grouped={}
                for x in rows:
                    grouped.setdefault(x.kind,[]).append({
                        "id":x.public_id,
                        "category":x.category,
                        "difficulty":x.difficulty,
                        "data":x.data,
                    })
                snapshot={
                    "schemaVersion":1,
                    "contentVersion":version,
                    "publishedAt":utcnow().isoformat(),
                    "games":grouped,
                }
                db.query(ContentRelease).update({ContentRelease.active:False})
                db.add(ContentRelease(
                    version=version,
                    note="Automatic Android live-content migration",
                    active=True,
                    item_count=len(rows),
                    snapshot=snapshot,
                ))
                for x in rows:
                    x.status="published"
                audit(db,"auto_publish_seed_migration",data={"version":version,"count":len(rows)})
                db.commit()

@router.get("/admin",response_class=HTMLResponse)
def admin_page():
    return Path(__file__).with_name("admin.html").read_text(encoding="utf-8")

@router.get("/v1/admin/content/summary")
def summary(principal: AdminPrincipal = Depends(require_viewer)):
    with SessionLocal() as db:
        rows=db.execute(select(ContentItem.kind,func.count(ContentItem.id)).group_by(ContentItem.kind)).all()
        drafts=db.scalar(select(func.count()).select_from(ContentItem).where(ContentItem.status=="draft"))
        active=db.scalar(select(ContentRelease).where(ContentRelease.active==True).order_by(ContentRelease.published_at.desc()))
        return {"counts":dict(rows),"drafts":drafts,"active_release":active.version if active else None}

@router.get("/v1/admin/content")
def list_content(kind:str|None=None,q:str|None=None,category:str|None=None,enabled:bool|None=None,page:int=1,page_size:int=50,
                 principal: AdminPrincipal = Depends(require_viewer)):
    page=max(1,page); page_size=min(200,max(1,page_size))
    with SessionLocal() as db:
        filters=[]
        if kind: filters.append(ContentItem.kind==kind)
        if category: filters.append(ContentItem.category==category)
        if enabled is not None: filters.append(ContentItem.enabled==enabled)
        if q:
            like="%"+q+"%"
            filters.append(ContentItem.public_id.ilike(like) | func.coalesce(ContentItem.category,"").ilike(like) | func.cast(ContentItem.data,String).ilike(like))
        stmt=select(ContentItem).where(*filters) if filters else select(ContentItem)
        count=select(func.count()).select_from(ContentItem).where(*filters) if filters else select(func.count()).select_from(ContentItem)
        total=db.scalar(count)
        rows=db.scalars(stmt.order_by(ContentItem.kind,ContentItem.category,ContentItem.public_id).offset((page-1)*page_size).limit(page_size)).all()
        return {"items":[item_json(x) for x in rows],"total":total,"page":page,"page_size":page_size}

@router.post("/v1/admin/content",status_code=201)
def create_content(body:ItemIn, principal: AdminPrincipal = Depends(require_editor_write)):
    with SessionLocal() as db:
        x=ContentItem(**body.model_dump(),status="draft")
        db.add(x); audit(db,"create",x,principal=principal); db.commit(); db.refresh(x); return item_json(x)

@router.patch("/v1/admin/content/{item_id}")
def patch_content(item_id:uuid.UUID,body:ItemPatch, principal: AdminPrincipal = Depends(require_editor_write)):
    with SessionLocal() as db:
        x=db.get(ContentItem,item_id)
        if not x: raise HTTPException(404,"Item not found")
        for k,v in body.model_dump(exclude_unset=True).items(): setattr(x,k,v)
        x.status="draft"; x.updated_at=utcnow(); audit(db,"update",x,principal=principal); db.commit(); db.refresh(x); return item_json(x)

@router.delete("/v1/admin/content/{item_id}",status_code=204)
def archive_content(item_id:uuid.UUID, principal: AdminPrincipal = Depends(require_editor_write)):
    with SessionLocal() as db:
        x=db.get(ContentItem,item_id)
        if not x: raise HTTPException(404,"Item not found")
        x.enabled=False; x.status="archived"; audit(db,"archive",x,principal=principal); db.commit()

@router.get("/v1/admin/releases")
def releases(principal: AdminPrincipal = Depends(require_viewer)):
    with SessionLocal() as db:
        rows=db.scalars(select(ContentRelease).order_by(ContentRelease.published_at.desc()).limit(50)).all()
        return [{"id":str(x.id),"version":x.version,"note":x.note,"active":x.active,"item_count":x.item_count,"published_at":x.published_at} for x in rows]

@router.post("/v1/admin/publish")
def publish(body:PublishIn, principal: AdminPrincipal = Depends(require_admin_write)):
    with SessionLocal() as db:
        rows=db.scalars(select(ContentItem).where(ContentItem.enabled==True,ContentItem.status!="archived").order_by(ContentItem.kind,ContentItem.public_id)).all()
        today=datetime.now(timezone.utc).strftime("%Y.%m.%d")
        existing=db.scalar(select(func.count()).select_from(ContentRelease).where(ContentRelease.version.like(today+".%")))
        version=f"{today}.{existing+1}"
        grouped={}
        for x in rows: grouped.setdefault(x.kind,[]).append({"id":x.public_id,"category":x.category,"difficulty":x.difficulty,"data":x.data})
        snapshot={"schemaVersion":1,"contentVersion":version,"publishedAt":utcnow().isoformat(),"games":grouped}
        db.query(ContentRelease).update({ContentRelease.active:False})
        db.add(ContentRelease(version=version,note=body.note,active=True,item_count=len(rows),snapshot=snapshot))
        for x in rows: x.status="published"
        audit(db,"publish",data={"version":version,"count":len(rows)},principal=principal)
        db.commit()
        return {"version":version,"item_count":len(rows)}

@router.post("/v1/admin/releases/{release_id}/activate")
def activate_release(release_id:uuid.UUID, principal: AdminPrincipal = Depends(require_admin_write)):
    with SessionLocal() as db:
        rel=db.get(ContentRelease,release_id)
        if not rel: raise HTTPException(404,"Release not found")
        db.query(ContentRelease).update({ContentRelease.active:False}); rel.active=True
        audit(db,"rollback",data={"version":rel.version},principal=principal); db.commit(); return {"active":rel.version}

@router.get("/v1/content/manifest")
def public_manifest():
    with SessionLocal() as db:
        rel=db.scalar(select(ContentRelease).where(ContentRelease.active==True).order_by(ContentRelease.published_at.desc()))
        if not rel: return {"schemaVersion":1,"contentVersion":None,"games":{}}
        return {"schemaVersion":1,"contentVersion":rel.version,"publishedAt":rel.published_at,
                "games":{k:{"url":f"/v1/content/{k}","count":len(v)} for k,v in rel.snapshot.get("games",{}).items()}}

@router.get("/v1/content/{kind}")
def public_content(kind:str):
    with SessionLocal() as db:
        rel=db.scalar(select(ContentRelease).where(ContentRelease.active==True).order_by(ContentRelease.published_at.desc()))
        if not rel: raise HTTPException(404,"No published content")
        return {"schemaVersion":1,"contentVersion":rel.version,"kind":kind,"items":rel.snapshot.get("games",{}).get(kind,[])}
