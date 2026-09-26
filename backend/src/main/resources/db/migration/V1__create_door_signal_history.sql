CREATE TABLE door_signal_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    signal_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_id VARCHAR(64),
    payload TEXT NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_door_signal_device_received
    ON door_signal_history (device_id, received_at);
