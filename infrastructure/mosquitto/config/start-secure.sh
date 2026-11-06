#!/bin/sh
set -eu

if [ ! -s /mosquitto/bootstrap/passwords ] || [ ! -f /mosquitto/bootstrap/acl ]; then
  echo 'Run python scripts/setup-mqtt.py init before starting the Broker.' >&2
  exit 1
fi

# Windows bind mounts do not preserve Linux ownership/mode bits. Keep the active
# security files in a private Linux volume, refreshed on each Broker restart.
cp /mosquitto/bootstrap/passwords /mosquitto/security/passwords
cp /mosquitto/bootstrap/acl /mosquitto/security/acl
chown mosquitto:mosquitto /mosquitto/security/passwords /mosquitto/security/acl
chmod 0600 /mosquitto/security/passwords /mosquitto/security/acl
exec /docker-entrypoint.sh mosquitto -c /mosquitto/config/mosquitto.conf
