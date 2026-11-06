-- Run only against a fresh disposable MySQL 8.4 database with init + mockdata.
DELIMITER //
CREATE PROCEDURE verify_smartdoor_v2()
BEGIN
    DECLARE rejected BOOLEAN DEFAULT FALSE;
    IF (SELECT COUNT(*) FROM accounts) <> 3
       OR (SELECT COUNT(*) FROM doors WHERE owner_account_id = 1) <> 2
       OR (SELECT COUNT(*) FROM doors WHERE status = 'UNCLAIMED') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected demo fixtures';
    END IF;
    START TRANSACTION;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 1452 SET rejected = TRUE;
        INSERT INTO rfid_credentials (door_id,account_id,cardholder_id,uid_lookup_value)
        VALUES (1,1,2,'INVALID-CROSS-ACCOUNT');
    END;
    IF NOT rejected THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cross-account RFID accepted';
    END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 1062 SET rejected = TRUE;
        INSERT INTO devices (door_id,device_code,node_type,lifecycle_status,activated_at)
        VALUES (1,'INVALID-DUPLICATE-SLOT','NODE_1','ACTIVE',UTC_TIMESTAMP(3));
    END;
    IF NOT rejected THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate active Node accepted';
    END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 1452 SET rejected = TRUE;
        INSERT INTO access_logs (event_id,door_id,source_device_id,method,result)
        VALUES ('INVALID-DEVICE-SCOPE',1,3,'PIN','DENIED');
    END;
    IF NOT rejected THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cross-door source accepted';
    END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 1452 SET rejected = TRUE;
        INSERT INTO access_logs (event_id,door_id,actor_account_id,method,result)
        VALUES ('INVALID-OWNER',1,2,'APP','UNKNOWN');
    END;
    IF NOT rejected THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cross-account App log accepted';
    END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 3819 SET rejected = TRUE;
        UPDATE doors SET status = 'SETUP_PENDING' WHERE id = 4;
    END;
    IF NOT rejected THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Claim without owner accepted';
    END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 1062 SET rejected = TRUE;
        INSERT INTO access_logs (event_id,door_id,method,result)
        VALUES ('ACCESS-RFID-01',1,'PIN','DENIED');
    END;
    IF NOT rejected THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate event accepted';
    END IF;
    -- Valid: same UID on another door, old inactive Nodes, atomic ownership.
    INSERT INTO rfid_credentials (door_id,account_id,cardholder_id,uid_lookup_value)
    VALUES (2,1,1,'DEMO-UID-REVOKED');
    INSERT INTO devices (door_id,device_code,node_type,lifecycle_status)
    VALUES (1,'VALID-PROVISIONED-SPARE','NODE_1','PROVISIONED');
    UPDATE doors SET owner_account_id = 3, status = 'SETUP_PENDING' WHERE id = 4;
    ROLLBACK;
    SELECT 'PASS: fixture and scope/lifecycle constraints' AS result;
END//
DELIMITER ;
CALL verify_smartdoor_v2();
DROP PROCEDURE verify_smartdoor_v2;
