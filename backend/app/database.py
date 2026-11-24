from pathlib import Path

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import DATABASE_URL


if DATABASE_URL.startswith("sqlite:"):
    Path(DATABASE_URL.removeprefix("sqlite:///" )).parent.mkdir(parents=True, exist_ok=True)
    engine = create_engine(
        DATABASE_URL,
        connect_args={"check_same_thread": False},
    )
else:
    engine = create_engine(DATABASE_URL, pool_pre_ping=True)


class Base(DeclarativeBase):
    pass


SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def prepare_development_schema() -> None:
    if DATABASE_URL.startswith("sqlite:"):
        inspector = inspect(engine)
        if "users" not in inspector.get_table_names():
            return

        columns = {column["name"]: column for column in inspector.get_columns("users")}
        needs_rebuild = (
            "phone_number" not in columns
            or "username" not in columns
            or columns.get("phone_number", {}).get("nullable") is False
        )
        if needs_rebuild:
            # Rebuild to make phone optional without losing existing users or listings.
            raw = engine.raw_connection()
            try:
                cursor = raw.cursor()
                cursor.execute("PRAGMA foreign_keys=OFF")
                cursor.execute("BEGIN IMMEDIATE")
                current = {row[1] for row in cursor.execute("PRAGMA table_info(users)").fetchall()}
                username = "username" if "username" in current else "NULL"
                phone = "phone_number" if "phone_number" in current else "NULL"
                email = "email" if "email" in current else "NULL"
                cursor.execute("""
                    CREATE TABLE users_new (
                        id VARCHAR(36) NOT NULL PRIMARY KEY,
                        username VARCHAR(32),
                        phone_number VARCHAR(16),
                        email VARCHAR(320),
                        display_name VARCHAR(80) NOT NULL,
                        password_hash VARCHAR(512) NOT NULL,
                        created_at DATETIME NOT NULL
                    )
                """)
                cursor.execute(f"""
                    INSERT INTO users_new (id, username, phone_number, email, display_name, password_hash, created_at)
                    SELECT id, {username}, {phone}, {email}, display_name, password_hash, created_at FROM users
                """)
                cursor.execute("DROP TABLE users")
                cursor.execute("ALTER TABLE users_new RENAME TO users")
                raw.commit()
            except Exception:
                raw.rollback()
                raise
            finally:
                raw.close()
        # Ensure legacy schemas also have the lookup/uniqueness indexes expected by the model.
        with engine.begin() as connection:
            connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_username ON users (username)"))
            connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_phone_number ON users (phone_number)"))
            connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_email ON users (email)"))
        if "signup_verifications" in inspect(engine).get_table_names():
            challenge_columns = {column["name"] for column in inspect(engine).get_columns("signup_verifications")}
            if "resend_count" not in challenge_columns:
                with engine.begin() as connection:
                    connection.execute(text(
                        "ALTER TABLE signup_verifications ADD COLUMN resend_count INTEGER NOT NULL DEFAULT 0"
                    ))
            with engine.begin() as connection:
                connection.execute(text(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_signup_verifications_email ON signup_verifications (email)"
                ))
                connection.execute(text(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_signup_verifications_username ON signup_verifications (username)"
                ))
        return

    # PostgreSQL production/deployment schema migration.
    if "users" not in inspect(engine).get_table_names():
        return
    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(32)"))
        connection.execute(text("ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL"))
        connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_username ON users (username)"))
        connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_phone_number ON users (phone_number)"))
        connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_email ON users (email)"))
        if "signup_verifications" in inspect(engine).get_table_names():
            challenge_columns = {column["name"] for column in inspect(engine).get_columns("signup_verifications")}
            if "resend_count" not in challenge_columns:
                connection.execute(text(
                    "ALTER TABLE signup_verifications ADD COLUMN resend_count INTEGER NOT NULL DEFAULT 0"
                ))
            connection.execute(text(
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_signup_verifications_email ON signup_verifications (email)"
            ))
            connection.execute(text(
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_signup_verifications_username ON signup_verifications (username)"
            ))


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
