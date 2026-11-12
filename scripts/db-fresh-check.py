"""Verify every Flyway migration on a NEW isolated MySQL schema, preserving the real database.

Requires the built Backend image and running Compose MySQL. Reproduces the V9
outbox restriction, then checks V10 with existing rows. Keeps the isolated test
schema for inspection; does not drop/reset any database.
"""
from datetime import datetime, timezone
import importlib.util
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_VERSIONS = ",".join(str(version) for version in range(1, 13))


def check_outbox_upgrade(query, migrate):
    """Exercise the real MySQL CHECKs; Hibernate-generated H2 tables omit them."""
    query("INSERT INTO doors (id,name) VALUES (1,'Migration test'),(2,'Other test door'); "
          "INSERT INTO devices (id,door_id,device_code,node_type) "
          "VALUES (1,1,'MIGRATION-NODE-1','NODE_1')")

    def insert(kind, attempts=0, status="PENDING", door_id=1):
        # All arguments are internal test constants, never user input or credentials.
        return ("INSERT INTO integration_outbox "
                "(kind,door_id,device_id,device_code,payload,status,attempts,next_attempt_at,created_at) "
                f"VALUES ('{kind}',{door_id},1,'MIGRATION-NODE-1','{{}}','{status}',{attempts},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")

    def rejected(sql, error_code):
        try:
            query("START TRANSACTION; " + sql + "; ROLLBACK")
        except RuntimeError as error:
            if "ERROR " + str(error_code) + " " not in str(error):
                raise
        else:
            raise RuntimeError("Expected MySQL error " + str(error_code) + " for invalid outbox data")

    for kind in ("CONFIGURATION", "CREDENTIALS"):
        query(insert(kind))
    snapshot_sql = "SELECT * FROM integration_outbox ORDER BY id"
    before = query(snapshot_sql)
    for kind in ("ACCESS_RESULT", "ALARM"):
        rejected(insert(kind), 3819)
    print("PASS reproduced V9 rejection of ACCESS_RESULT and ALARM", flush=True)

    migrate()
    if query(snapshot_sql) != before:
        raise RuntimeError("V10 changed existing outbox rows")
    for kind in ("CONFIGURATION", "CREDENTIALS", "ACCESS_RESULT", "ALARM"):
        query("START TRANSACTION; " + insert(kind) + "; ROLLBACK")
    rejected(insert("UNKNOWN_KIND"), 3819)
    rejected(insert("ACCESS_RESULT", attempts=-1), 3819)
    rejected(insert("ACCESS_RESULT", status="PROCESSING"), 3819)
    rejected(insert("ALARM", status="SENT"), 3819)
    rejected(insert("ALARM", door_id=2), 1452)
    if query(snapshot_sql) != before:
        raise RuntimeError("Outbox test transactions were not rolled back")
    print("PASS V10: all four kinds accepted; invalid kinds, attempts, leases, timestamps and cross-door FK rejected; existing rows preserved", flush=True)


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def check_close_upgrade(query, migrate):
    query("INSERT INTO accounts (id,display_name,username,password_hash) "
          "VALUES (1,'Migration owner','migration-owner','migration-test-only'); "
          "UPDATE doors SET owner_account_id=1,status='ACTIVE' WHERE id=1; "
          "INSERT INTO control_commands (id,door_id,account_id,device_code,status,created_at,expires_at) "
          "VALUES ('11111111-1111-4111-8111-111111111111',1,1,'MIGRATION-NODE-2','EXECUTED',"
          "UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 15 SECOND))")
    before = query("SELECT id,status,created_at,expires_at FROM control_commands ORDER BY id")
    migrate()
    if query("SELECT id,status,created_at,expires_at FROM control_commands ORDER BY id") != before:
        raise RuntimeError("V11 changed existing command data")
    if query("SELECT action FROM control_commands WHERE id='11111111-1111-4111-8111-111111111111'") != "UNLOCK":
        raise RuntimeError("V11 did not default the old command to UNLOCK")
    result = query("START TRANSACTION; "
          "INSERT INTO control_commands (id,door_id,account_id,device_code,status,action,created_at,expires_at) "
          "VALUES ('22222222-2222-4222-8222-222222222222',1,1,'MIGRATION-NODE-2','PENDING','CLOSE',"
          "UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 15 SECOND)); "
          "SELECT action FROM control_commands WHERE id='22222222-2222-4222-8222-222222222222'; ROLLBACK")
    if result != "CLOSE":
        raise RuntimeError("V11 could not persist a CLOSE command")
    print("PASS V11: old commands preserved as UNLOCK; CLOSE persisted", flush=True)


def check_revocation_upgrade(query, migrate):
    query("INSERT INTO device_transport_credentials (device_id,credential_id,created_at) "
          "VALUES (1,'33333333-3333-4333-8333-333333333333',UTC_TIMESTAMP(3))")
    before = query("SELECT device_id,credential_id,created_at FROM device_transport_credentials")
    migrate()
    if before != query("SELECT device_id,credential_id,created_at FROM device_transport_credentials"):
        raise RuntimeError("V12 changed existing credentials")
    if query("SELECT broker_revoked_at IS NULL FROM device_transport_credentials WHERE device_id=1") != "1":
        raise RuntimeError("V12 incorrectly marked old credentials as Broker-revoked")
    for assignments in ("broker_revoked_at=UTC_TIMESTAMP(3)",
                        "revoked_at=UTC_TIMESTAMP(3),broker_revoked_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND)"):
        try:
            query("START TRANSACTION; UPDATE device_transport_credentials SET " + assignments + " WHERE device_id=1; ROLLBACK")
        except RuntimeError as error:
            if "ERROR 3819 " not in str(error):
                raise
        else:
            raise RuntimeError("V12 accepted invalid Broker revocation proof")
    query("START TRANSACTION; UPDATE device_transport_credentials SET revoked_at=UTC_TIMESTAMP(3),"
          "broker_revoked_at=UTC_TIMESTAMP(3) WHERE device_id=1; ROLLBACK")
    print("PASS V12: credentials preserved; proof defaults NULL; revocation ordering CHECK enforced", flush=True)


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
    def migrate(target):
        result = subprocess.run(["docker", "compose", "run", "--rm", "--no-deps", "--user", "0",
            "--volume", str(folder) + ":/run/local", "backend",
            "--spring.profiles.active=local,device-security", "--app.settings-mqtt.enabled=false", "--app.outbox.enabled=false",
            "--spring.datasource.url=jdbc:mysql://mysql:3306/" + schema + "?connectionTimeZone=UTC",
            "--spring.flyway.target=" + str(target),
            "--spring.jpa.hibernate.ddl-auto=" + ("validate" if target >= 12 else "none"),
            "--security.action=check-schema", "--security.output=/run/local/schema-v" + str(target) + ".local.json"],
            cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
        log = folder / ("migration-v" + str(target) + ".local.log")
        log.write_text(result.stdout + result.stderr, encoding="utf-8")
        log.chmod(0o600)
        if result.returncode:
            raise RuntimeError("Migration failed; inspect " + str(log.relative_to(ROOT)))

    migrate(9)
    check_outbox_upgrade(lambda sql: audit.query("USE `" + schema + "`; " + sql), lambda: migrate(10))
    check_close_upgrade(lambda sql: audit.query("USE `" + schema + "`; " + sql), lambda: migrate(11))
    check_revocation_upgrade(lambda sql: audit.query("USE `" + schema + "`; " + sql), lambda: migrate(12))
    versions = audit.query("SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM `" + schema + "`.flyway_schema_history WHERE success=1")
    if versions != EXPECTED_VERSIONS:
        raise RuntimeError("Unexpected fresh migration history: " + versions)
    tables = audit.query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + schema + "'")
    foreign_keys = audit.query("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='" + schema + "'")
    if tables != "20" or foreign_keys != "31":
        raise RuntimeError("Fresh schema has unexpected table/FK counts: " + tables + "/" + foreign_keys)
    print("PASS Flyway V1-V12 and Hibernate validation: 19 business tables, 31 foreign keys")
    print("Isolated test schema kept for inspection:", schema)
    print("Migration logs:", folder.relative_to(ROOT))
    print("Existing application database was preserved.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, KeyError, subprocess.SubprocessError) as error:
        print("FAIL", type(error).__name__, str(error), file=sys.stderr)
        sys.exit(1)
