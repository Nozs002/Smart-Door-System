"""Verify every Flyway migration on a NEW isolated MySQL schema, preserving the real database.

Requires the built Backend image and running Compose MySQL. Keeps the clearly
named empty test schema for inspection; does not drop/reset any database.
"""
from datetime import datetime, timezone
import importlib.util
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def main():
    setup = module("mqtt_setup", "setup-mqtt.py")
    audit = module("db_audit", "db-check.py")
    username = setup.environment()["MYSQL_USER"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,100}", username):
        raise RuntimeError("MYSQL_USER requires manual grant review before isolated schema testing.")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    schema = "p14_schema_check_" + stamp
    audit.query("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; "
                "GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '" + username + "'@'%'")
    folder = ROOT / ".local" / "schema-check" / stamp
    folder.mkdir(parents=True, exist_ok=False)
    result = subprocess.run(["docker", "compose", "run", "--rm", "--no-deps", "--user", "0",
        "--volume", str(folder) + ":/run/local", "backend",
        "--spring.profiles.active=local,device-security", "--app.settings-mqtt.enabled=false", "--app.outbox.enabled=false",
        "--spring.datasource.url=jdbc:mysql://mysql:3306/" + schema + "?connectionTimeZone=UTC",
        "--security.action=export-broker", "--security.output=/run/local/empty-users.local.json"],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
    log = folder / "migration.local.log"
    log.write_text(result.stdout + result.stderr, encoding="utf-8")
    log.chmod(0o600)
    if result.returncode:
        raise RuntimeError("Fresh migration failed; inspect " + str(log.relative_to(ROOT)))
    versions = audit.query("SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM `" + schema + "`.flyway_schema_history WHERE success=1")
    if versions != "1,2,3,4,5,6":
        raise RuntimeError("Unexpected fresh migration history: " + versions)
    tables = audit.query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + schema + "'")
    foreign_keys = audit.query("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='" + schema + "'")
    if tables != "18" or foreign_keys != "27":
        raise RuntimeError("Fresh schema has unexpected table/FK counts: " + tables + "/" + foreign_keys)
    print("PASS fresh Flyway V1-V6 and Hibernate validation: 17 business tables, 27 foreign keys")
    print("Isolated test schema kept for inspection:", schema)
    print("Migration log:", log.relative_to(ROOT))
    print("Existing application database was preserved.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, KeyError, subprocess.SubprocessError) as error:
        print("FAIL", type(error).__name__, str(error), file=sys.stderr)
        sys.exit(1)
