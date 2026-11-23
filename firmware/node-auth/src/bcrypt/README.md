# Vendored BCrypt

`crypt_blowfish.c` and `crypt_blowfish.h` are unmodified Openwall sources at
commit `3354bb81eea489e972b0a7c63231514ab34f73a0`:
https://github.com/openwall/crypt_blowfish/tree/3354bb81eea489e972b0a7c63231514ab34f73a0

The upstream public-domain dedication and permissive fallback license are retained
in both files. Arduino builds this directory recursively. `CredentialSnapshot.h`
wraps verification with a bounded cost and digit-only PIN validation. Online
access decisions still belong to Backend; the verifier does not enable offline
unlock or bypass lifecycle/ownership/lockout checks.
