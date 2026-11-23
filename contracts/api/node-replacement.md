# Owner-authorized Node replacement — V14

Account identity comes from JWT. All endpoints return the existing ApiResponse
envelope with `Cache-Control: no-store`. TECHNICIAN role does not grant Door
ownership. No endpoint accepts a caller-supplied Broker-revocation flag.

| Method | Endpoint | Authorization / body |
| --- | --- | --- |
| POST | `/api/v1/doors/{doorId}/devices/{deviceId}/replacement-requests` | Owner; `{requestId: UUID, reason}`; create/retry one scoped grant |
| GET | `/api/v1/doors/{doorId}/devices/{deviceId}/replacement-request` | Owner; latest request for the old Node, or null |
| POST | `/api/v1/technician/replacements` | TECHNICIAN; `{authorizationCode, unitCode}`; consume grant and bind own unused inventory of matching role |
| GET | `/api/v1/technician/replacements` | TECHNICIAN; own bound replacement requests only |
| POST | `/api/v1/doors/{doorId}/replacement-requests/{id}/complete` | Owner; begin/retry cutover; return REVOKING until current ACKs and Broker proof allow activation |
| POST | `/api/v1/doors/{doorId}/replacement-requests/{id}/cancel` | Owner; OPEN/BOUND only; revoke/retire staged identity if present |

Creation requires a claimed, non-DISABLED Door and an ACTIVE/DISABLED/RETIRED
source. A RETIRED source is never reactivated. Another non-retired Node in the
same role prevents registration of a competing candidate. Door and inventory
locks plus a MySQL generated unique pending-role slot serialize concurrent
requests. Exact binding retries return the same result; a consumed grant cannot
bind a different board or technician. A grant expires for binding after 24 hours;
BOUND work can still be completed by the owner after that deadline.

Grant code format is `RPL-` followed by 20 uppercase hex characters. Only its
SHA-256 is persisted; its value is derived with a dedicated HMAC purpose and
returned to the owner while OPEN/unexpired. The technician enters the
authorization code and the replacement board's `unitCode` in the App.

Factory headers must be exported before the replacement board is bound to a
claimed Door. Binding creates a PROVISIONED Device and its transport identity,
then connects inventory to that Device. The owner configures its Wi-Fi and
requests the current configuration/credential snapshots via `/sync/retry`.

Owner response fields include `id`, `doorId`, `doorName`, `oldDeviceId`,
`newDeviceId`, `oldDeviceCode`, optional `newDeviceCode`, `nodeType`, `status`,
`reason`, `expiresAt`, `completedAt`, `ready`, `brokerRevoked`, optional
`authorizationCode`, and optional `newNodeSetup` for BOUND/REVOKING. Technician
responses exclude authorizationCode and newNodeSetup. No response includes
MQTT passwords, signing keys, or bootstrap secrets.

States: OPEN → BOUND → REVOKING → COMPLETED. OPEN can expire; OPEN/BOUND can be
cancelled. Cancellation retires the staged identity, never the old working
Node. After REVOKING, old credentials are irreversible; retry completion rather
than restoring keys. COMPLETED retries do not emit duplicate lifecycle events.

Before cutover, the replacement needs a verified transport identity, latest
configuration ACK and Node 1's ACTIVE PIN/current credential ACK. On confirmation
the old key is revoked, an ACTIVE old Node is disabled, and the request becomes
REVOKING. Broker supervisor restarts to disconnect established sessions and
tests the old login. Only CONNACK auth refusal establishes proof. Backend imports
the proof from a read-only private volume and matches device/credential/epoch.
Unverified removal never activates the replacement. Final transaction retires
the source if needed, activates the new Node, preserves history/owner/Door ID,
and records linked replacement events. ACTIVATE/complete-setup cannot bypass
the staged request. Existing `/devices/{id}/replace` still enforces readiness
and Broker proof and reconciles the associated request when used.

HTTP errors use normal 401/403/404 ownership/role handling, 400 invalid input,
and 409 expired/used grant, occupied slot, wrong role, missing readiness or
invalid state. A valid completion awaiting proof returns 200 with REVOKING;
it does not claim COMPLETED. Local cutover may briefly reconnect other Broker
clients. Retirement does not erase physical NVS or stop offline local logic.

[Handover and UI walkthrough](../../docs/05-backend/node-replacement.md).
