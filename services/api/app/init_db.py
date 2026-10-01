from app.main import Base, engine
Base.metadata.create_all(bind=engine)
print("database schema ready")
