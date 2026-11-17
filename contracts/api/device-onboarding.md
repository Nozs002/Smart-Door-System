# Device onboarding API

Account identity comes from JWT. Responses use the existing ApiResponse envelope.
Sensitive responses have `Cache-Control: no-store`. Accounts registered publicly
receive CUSTOMER. TECHNICIAN is created by the deployment bootstrap environment.

| Method | Endpoint | Authorization and behavior |
| --- | --- | --- |
| GET | `/api/v1/technician/inventory` | Technician's registered boards |
| POST | `/api/v1/technician/inventory` | `{nodeType: NODE_1 or NODE_2}` registers a unique board identity |
| GET | `/api/v1/technician/inventory/{unitCode}/factory` | Creator technician obtains setup label and private firmware header before claim |
| GET | `/api/v1/technician/kits` | Technician's installation kits |
| POST | `/api/v1/technician/kits` | `{requestId,name,location,node1Code,node2Code}` provisions a Door and binds two boards atomically |
| POST | `/api/v1/technician/kits/{requestId}/claim-code` | Reissues a seven-day code while unclaimed |
| GET | `/api/v1/doors/{doorId}/setup-nodes` | Door owner obtains each board's setup SSID/code |
| POST | `/api/v1/doors/{doorId}/complete-setup` | Owner activates both ready Nodes in one transaction |
| POST | `/api/v1/device-enrollment` | Board authenticates using `{unitCode,bootstrapSecret}` and retrieves its binding/transport identity after claim |

Kit retries reuse the same UUID requestId. A committed retry returns the existing
kit without issuing another code. If the initial response was lost, the
technician can explicitly reissue a claim code. A board can belong to one Device.
Stable inventory locks prevent concurrent binding. Node roles must match.

Factory output includes `unitCode`, `nodeType`, `setupCode`, `setupSsid` and
`firmwareConfig`. The bootstrap secret belongs only in the prepared board's
private header. The QR label contains the unit code and local setup code.
Factory exports are blocked after the Door is claimed. Setup information is
then available to the owner through the setup-nodes endpoint.

Enrollment output contains `deviceId`, `doorId`, `deviceCode`, `nodeType`,
`credentialId`, `username`, `password`, `signingKey`, `mqttHost`, and `mqttPort`.
Revoked credentials and RETIRED devices cannot enroll. Before binding/claim the
endpoint returns 409. Invalid bootstrap authentication returns 401.

Completion requires exactly one usable Node for each role, an ACTIVE PIN,
current configuration ACKs and Node 1's current credential ACK. Disabled Nodes
require an explicit lifecycle action. A completed request can be retried.
Readiness checks and both activations share a Door lock and transaction.

## QR and manual inputs

| Purpose | QR representation | Manual equivalent |
| --- | --- | --- |
| Board identity | `smartdoor://unit/{unitCode}?setup={setupCode}` | Unit code printed on the label |
| Door claim | `smartdoor://claim/{activationCode}` | Activation code printed on handover sheet |
| Local Wi-Fi setup | Same board QR, including setup parameter | Setup code printed on the board label |

QR is an input mechanism. All paths use the same server authorization and
validation. Camera denial leaves manual entry available.

## Local ESP32 setup HTTP

Hold BOOT/GPIO0 for three seconds while firmware is running to open its
password-protected `SmartDoor-{first eight unit-code characters}` access point.
It also opens on first startup without stored Wi-Fi. The setup password is the
16-character setup code. The portal expires after 20 minutes.

| Method | Local endpoint | Result |
| --- | --- | --- |
| GET | `http://192.168.4.1/api/info` | Board unitCode/nodeType and connectivity flags |
| GET | `http://192.168.4.1/api/networks` | Asynchronous Wi-Fi scan result |
| POST | `http://192.168.4.1/api/wifi` | `{ssid,password}`, persist/read back locally, acknowledge 202, then connect |

Network scan and Wi-Fi writes require `X-Setup-Code`. The App verifies the
connected board identity before sending credentials. Household passwords travel
directly to the board and are persisted in its NVS. Backend does not receive them.
HTTPS enrollment validates the configured root CA. Local HTTP enrollment and MQTT
TCP are available for the trusted LAN demo. Operational firmware currently uses
the project's MQTT TCP transport.
