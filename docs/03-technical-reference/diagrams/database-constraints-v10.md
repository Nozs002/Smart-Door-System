# Ràng buộc ERD V10

Sinh từ metadata MySQL V10; chỉ chứa tên bảng, cột và ràng buộc.

## Khóa ngoại (31)

| Bảng con | Cột | Bảng cha | Cột được tham chiếu |
| --- | --- | --- | --- |
| `access_logs` | `door_id` | `doors` | `id` |
| `access_logs` | `door_id, actor_account_id` | `doors` | `id, owner_account_id` |
| `access_logs` | `rfid_credential_id, door_id` | `rfid_credentials` | `id, door_id` |
| `access_logs` | `source_device_id, door_id` | `devices` | `id, door_id` |
| `access_logs` | `cardholder_id` | `cardholders` | `id` |
| `alerts` | `door_id` | `doors` | `id` |
| `alerts` | `source_device_id, door_id` | `devices` | `id, door_id` |
| `cardholders` | `account_id` | `accounts` | `id` |
| `control_commands` | `door_id, account_id` | `doors` | `id, owner_account_id` |
| `device_lifecycle_events` | `device_id, door_id` | `devices` | `id, door_id` |
| `device_lifecycle_events` | `replacement_device_id, door_id` | `devices` | `id, door_id` |
| `device_lifecycle_events` | `door_id, actor_account_id` | `doors` | `id, owner_account_id` |
| `device_transport_credentials` | `device_id` | `devices` | `id` |
| `devices` | `door_id` | `doors` | `id` |
| `door_activation_codes` | `door_id` | `doors` | `id` |
| `door_activation_codes` | `consumed_by_account_id` | `accounts` | `id` |
| `door_configs` | `door_id` | `doors` | `id` |
| `door_telemetry` | `door_id` | `doors` | `id` |
| `door_telemetry` | `source_device_id` | `devices` | `id` |
| `doors` | `owner_account_id` | `accounts` | `id` |
| `integration_outbox` | `door_id` | `doors` | `id` |
| `integration_outbox` | `device_id, door_id` | `devices` | `id, door_id` |
| `lockout_statuses` | `door_id` | `doors` | `id` |
| `owner_notifications` | `account_id` | `accounts` | `id` |
| `owner_notifications` | `alert_id, door_id` | `alerts` | `id, door_id` |
| `owner_notifications` | `door_id, account_id` | `doors` | `id, owner_account_id` |
| `processed_device_messages` | `device_id` | `devices` | `id` |
| `revoked_access_tokens` | `account_id` | `accounts` | `id` |
| `rfid_credentials` | `door_id, account_id` | `doors` | `id, owner_account_id` |
| `rfid_credentials` | `cardholder_id, account_id` | `cardholders` | `id, account_id` |
| `system_pins` | `door_id` | `doors` | `id` |

## Khóa duy nhất / khóa chính

| Bảng | Khóa | Cột |
| --- | --- | --- |
| `access_logs` | `PRIMARY` | `id` |
| `access_logs` | `uk_access_event` | `event_id` |
| `accounts` | `PRIMARY` | `id` |
| `accounts` | `uk_accounts_username` | `username` |
| `alerts` | `PRIMARY` | `id` |
| `alerts` | `uk_alert_event` | `event_id` |
| `alerts` | `uk_alert_scope` | `id, door_id` |
| `cardholders` | `PRIMARY` | `id` |
| `cardholders` | `uk_cardholders_account` | `id, account_id` |
| `control_commands` | `PRIMARY` | `id` |
| `device_lifecycle_events` | `PRIMARY` | `id` |
| `device_lifecycle_events` | `uk_lifecycle_event` | `event_id` |
| `device_transport_credentials` | `PRIMARY` | `device_id` |
| `device_transport_credentials` | `uk_transport_credential` | `credential_id` |
| `devices` | `PRIMARY` | `id` |
| `devices` | `uk_devices_active_slot` | `door_id, active_node_type` |
| `devices` | `uk_devices_code` | `device_code` |
| `devices` | `uk_devices_scope` | `id, door_id` |
| `door_activation_codes` | `PRIMARY` | `id` |
| `door_activation_codes` | `uk_activation_code_hash` | `code_hash` |
| `door_configs` | `PRIMARY` | `door_id` |
| `door_telemetry` | `event_id` | `event_id` |
| `door_telemetry` | `PRIMARY` | `id` |
| `doors` | `PRIMARY` | `id` |
| `doors` | `uk_doors_owner` | `id, owner_account_id` |
| `flyway_schema_history` | `PRIMARY` | `installed_rank` |
| `integration_outbox` | `PRIMARY` | `id` |
| `lockout_statuses` | `PRIMARY` | `door_id, method` |
| `owner_notifications` | `PRIMARY` | `id` |
| `owner_notifications` | `uk_notification_alert_owner` | `alert_id, account_id` |
| `processed_device_messages` | `PRIMARY` | `id` |
| `revoked_access_tokens` | `PRIMARY` | `token_id` |
| `rfid_credentials` | `PRIMARY` | `id` |
| `rfid_credentials` | `uk_rfid_door_uid` | `door_id, uid_lookup_value` |
| `rfid_credentials` | `uk_rfid_scope` | `id, door_id` |
| `system_pins` | `PRIMARY` | `door_id` |

## CHECK

| Bảng | Ràng buộc | Biểu thức |
| --- | --- | --- |
| `access_logs` | `access_logs_chk_1` | `` ((`method` <> _utf8mb4'APP') or (`actor_account_id` is not null)) `` |
| `access_logs` | `access_logs_chk_2` | `` ((`method` = _utf8mb4'APP') or (`actor_account_id` is null)) `` |
| `alerts` | `alerts_chk_1` | `` ((`status` <> _utf8mb4'RESOLVED') or (`resolved_at` is not null)) `` |
| `devices` | `devices_chk_1` | `` ((`lifecycle_status` <> _utf8mb4'ACTIVE') or (`activated_at` is not null)) `` |
| `devices` | `devices_chk_2` | `` ((`lifecycle_status` <> _utf8mb4'RETIRED') or (`retired_at` is not null)) `` |
| `door_activation_codes` | `door_activation_codes_chk_1` | `` (`expires_at` > `created_at`) `` |
| `door_activation_codes` | `door_activation_codes_chk_2` | `` (((`consumed_at` is null) and (`consumed_by_account_id` is null)) or ((`consumed_at` is not null) and (`consumed_by_account_id` is not null))) `` |
| `door_configs` | `chk_open_alert_timeout` | `` ((`door_open_alert_enabled` = false) or (`open_timeout_seconds` is not null)) `` |
| `door_configs` | `door_configs_chk_1` | `` ((`max_failed_attempts` > 0) and (`lockout_seconds` > 0) and (`version` > 0)) `` |
| `door_configs` | `door_configs_chk_2` | `` ((`open_timeout_seconds` is null) or (`open_timeout_seconds` > 0)) `` |
| `doors` | `chk_doors_owner` | `` (((`status` = _utf8mb4'UNCLAIMED') and (`owner_account_id` is null)) or ((`status` <> _utf8mb4'UNCLAIMED') and (`owner_account_id` is not null))) `` |
| `integration_outbox` | `chk_outbox_kind` | `` (`kind` in (_utf8mb4'CONFIGURATION',_utf8mb4'CREDENTIALS',_utf8mb4'ACCESS_RESULT',_utf8mb4'ALARM')) `` |
| `integration_outbox` | `integration_outbox_chk_2` | `` (`attempts` >= 0) `` |
| `integration_outbox` | `integration_outbox_chk_3` | `` (((`status` = _utf8mb4'PROCESSING') and (`lease_until` is not null) and (`lease_token` is not null)) or ((`status` <> _utf8mb4'PROCESSING') and (`lease_until` is null) and (`lease_token` is null))) `` |
| `integration_outbox` | `integration_outbox_chk_4` | `` ((`status` <> _utf8mb4'SENT') or (`sent_at` is not null)) `` |
| `owner_notifications` | `owner_notifications_chk_1` | `` ((`read_at` is null) or (`read_at` >= `created_at`)) `` |
| `rfid_credentials` | `rfid_credentials_chk_1` | `` ((`status` <> _utf8mb4'REVOKED') or (`revoked_at` is not null)) `` |
| `system_pins` | `system_pins_chk_1` | `` (`version` > 0) `` |
