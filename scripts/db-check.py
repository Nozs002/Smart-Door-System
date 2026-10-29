"""Read-only MySQL audit and optional local backup; credentials stay inside Docker."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MYSQL = 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --user=root --database="$MYSQL_DATABASE" --batch --skip-column-names'
CHECKS = {
    "door_owner_status": "SELECT COUNT(*) FROM doors WHERE (status='UNCLAIMED' AND owner_account_id IS NOT NULL) OR (status<>'UNCLAIMED' AND owner_account_id IS NULL)",
    "cardholder_account_orphans": "SELECT COUNT(*) FROM cardholders c LEFT JOIN accounts a ON a.id=c.account_id WHERE a.id IS NULL",
    "active_node_slot_duplicates": "SELECT COUNT(*) FROM (SELECT door_id,node_type FROM devices WHERE lifecycle_status='ACTIVE' GROUP BY door_id,node_type HAVING COUNT(*)>1) x",
    "device_door_orphans": "SELECT COUNT(*) FROM devices d LEFT JOIN doors r ON r.id=d.door_id WHERE r.id IS NULL",
    "activation_hash_duplicates": "SELECT COUNT(*) FROM (SELECT code_hash FROM door_activation_codes GROUP BY code_hash HAVING COUNT(*)>1) x",
    "activation_consumption_pair": "SELECT COUNT(*) FROM door_activation_codes WHERE (consumed_at IS NULL)<>(consumed_by_account_id IS NULL)",
    "rfid_account_scope": "SELECT COUNT(*) FROM rfid_credentials r JOIN doors d ON d.id=r.door_id JOIN cardholders c ON c.id=r.cardholder_id WHERE r.account_id<>d.owner_account_id OR r.account_id<>c.account_id",
    "lifecycle_device_scope": "SELECT COUNT(*) FROM device_lifecycle_events e JOIN devices d ON d.id=e.device_id LEFT JOIN devices r ON r.id=e.replacement_device_id WHERE e.door_id<>d.door_id OR (e.replacement_device_id IS NOT NULL AND e.door_id<>r.door_id)",
    "access_log_scope": "SELECT COUNT(*) FROM access_logs l LEFT JOIN devices d ON d.id=l.source_device_id LEFT JOIN rfid_credentials r ON r.id=l.rfid_credential_id WHERE (l.source_device_id IS NOT NULL AND l.door_id<>d.door_id) OR (l.rfid_credential_id IS NOT NULL AND l.door_id<>r.door_id)",
    "app_log_actor": "SELECT COUNT(*) FROM access_logs WHERE (method='APP' AND actor_account_id IS NULL) OR (method<>'APP' AND actor_account_id IS NOT NULL)",
    "alert_device_scope": "SELECT COUNT(*) FROM alerts a JOIN devices d ON d.id=a.source_device_id WHERE a.door_id<>d.door_id",
    "resolved_alert_timestamp": "SELECT COUNT(*) FROM alerts WHERE status='RESOLVED' AND resolved_at IS NULL",
    "active_device_timestamp": "SELECT COUNT(*) FROM devices WHERE lifecycle_status='ACTIVE' AND activated_at IS NULL",
    "retired_device_timestamp": "SELECT COUNT(*) FROM devices WHERE lifecycle_status='RETIRED' AND retired_at IS NULL",
}
MIGRATED_CHECKS = {
    "revoked_access_tokens": {
        "revocation_account_scope": "SELECT COUNT(*) FROM revoked_access_tokens r LEFT JOIN accounts a ON a.id=r.account_id WHERE a.id IS NULL",
    },
    "integration_outbox": {
        "outbox_device_scope": "SELECT COUNT(*) FROM integration_outbox o JOIN devices d ON d.id=o.device_id WHERE o.door_id<>d.door_id OR BINARY o.device_code<>BINARY d.device_code",
        "outbox_lease_shape": "SELECT COUNT(*) FROM integration_outbox WHERE (status='PROCESSING')<>(lease_until IS NOT NULL AND lease_token IS NOT NULL) OR (status<>'PROCESSING' AND (lease_until IS NOT NULL OR lease_token IS NOT NULL))",
        "outbox_sent_timestamp": "SELECT COUNT(*) FROM integration_outbox WHERE status='SENT' AND sent_at IS NULL",
    },
    "owner_notifications": {
        "notification_owner_scope": "SELECT COUNT(*) FROM owner_notifications n JOIN doors d ON d.id=n.door_id JOIN alerts a ON a.id=n.alert_id WHERE n.account_id<>d.owner_account_id OR n.door_id<>a.door_id",
        "notification_unique_delivery": "SELECT COUNT(*) FROM (SELECT alert_id,account_id FROM owner_notifications GROUP BY alert_id,account_id HAVING COUNT(*)>1) x",
    },
    "device_transport_credentials": {
        "transport_device_scope": "SELECT COUNT(*) FROM device_transport_credentials c LEFT JOIN devices d ON d.id=c.device_id WHERE d.id IS NULL",
        "transport_identity_duplicates": "SELECT COUNT(*) FROM (SELECT credential_id FROM device_transport_credentials GROUP BY credential_id HAVING COUNT(*)>1) x",
    },
    "processed_device_messages": {
        "message_device_scope": "SELECT COUNT(*) FROM processed_device_messages m LEFT JOIN devices d ON d.id=m.device_id WHERE d.id IS NULL",
    },
}


def query(sql: str) -> str:
    result = subprocess.run(
        ["docker", "compose", "exec", "-T", "mysql", "sh", "-c", MYSQL],
        input=sql + ";\n", text=True, capture_output=True, cwd=ROOT,
        encoding="utf-8", errors="replace", timeout=60,
    )
    if result.returncode:
        raise RuntimeError("MySQL query failed: " + result.stderr.strip())
    return result.stdout.strip()


def backup() -> Path:
    folder = ROOT / ".local" / "backups"
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / ("before-person1-person4-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + ".sql")
    command = 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump --user=root --single-transaction --routines --triggers --no-tablespaces --databases "$MYSQL_DATABASE"'
    with target.open("xb") as stream:
        result = subprocess.run(["docker", "compose", "exec", "-T", "mysql", "sh", "-c", command],
                                stdout=stream, stderr=subprocess.PIPE, cwd=ROOT, timeout=120)
    if result.returncode:
        target.unlink(missing_ok=True)
        raise RuntimeError("Backup failed: " + result.stderr.decode("utf-8", errors="replace"))
    target.chmod(0o600)
    return target


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup", action="store_true", help="Write a private SQL dump under ignored .local/backups before checking.")
    parser.add_argument("--expect-version", type=int, help="Fail when this Flyway migration has not been applied successfully.")
    parser.add_argument("--door-id", type=int, help="Also print safe device/outbox diagnostics for this Door (never payloads or keys).")
    args = parser.parse_args()
    if args.backup:
        print("Backup saved:", backup().relative_to(ROOT))
    print("Migration history (version, description, success):")
    print(query("SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank"))
    print("Schema tables:")
    tables = set(query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME").splitlines())
    print("\n".join(sorted(tables)))
    failures = 0
    checks = dict(CHECKS)
    for table, extra in MIGRATED_CHECKS.items():
        if table in tables:
            checks.update(extra)
    if args.expect_version is not None:
        applied = int(query("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='" + str(args.expect_version) + "'"))
        failures += applied != 1
        print(("PASS" if applied == 1 else "FAIL") + " migration_version_" + str(args.expect_version))
    statements = ";\n".join("SELECT '" + name + "', (" + sql + ")" for name, sql in checks.items())
    for row in query(statements).splitlines():
        name, result = row.split("\t", 1)
        count = int(result)
        failures += bool(count)
        print(("PASS" if not count else "FAIL") + " " + name + ": " + str(count))
    print("Foreign keys:", query("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()"))
    print("NOTE active_nodes_without_config_ack:", query("SELECT COUNT(*) FROM devices WHERE lifecycle_status='ACTIVE' AND applied_config_version IS NULL"))
    print("NOTE legacy_placeholder_rfid:", query("SELECT COUNT(*) FROM rfid_credentials WHERE uid_lookup_value NOT REGEXP '^[A-F0-9]+$' OR LENGTH(uid_lookup_value) NOT IN (8,14,20)"))
    if args.door_id is not None:
        if args.door_id < 1:
            parser.error("door-id must be positive")
        scope = str(args.door_id)
        print("Devices: id, code, lifecycle, applied config, applied credentials")
        print(query("SELECT id,device_code,lifecycle_status,applied_config_version,applied_pin_version FROM devices WHERE door_id=" + scope))
        if "integration_outbox" in tables:
            print("Outbox: kind, status, attempts, last error, count")
            print(query("SELECT kind,status,attempts,last_error,COUNT(*) FROM integration_outbox WHERE door_id=" + scope + " GROUP BY kind,status,attempts,last_error"))
    print("Audit result:", "PASS" if not failures else "FAIL")
    return 1 if failures else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
