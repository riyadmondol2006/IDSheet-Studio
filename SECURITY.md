# Security policy

## Reporting a vulnerability

Please report security issues privately through
[GitHub Security Advisories](https://github.com/riyadmondol2006/IDSheet-Studio/security/advisories/new)
instead of opening a public issue. You will receive a response as soon as possible.

## How IDSheet Studio protects you

- The remove.bg API key is encrypted with an AES-256-GCM key held in the Android Keystore and is
  excluded from backups.
- Photos and projects are stored only in the app's private storage. Images are sent to
  remove.bg only when you request background removal, over HTTPS.
- Cleartext network traffic is disabled.
- In-app updates are downloaded from GitHub Releases over HTTPS and installed only after the
  SHA-256 checksum, package name, version code and signing certificate have been verified. The
  Android installer always asks for your confirmation.
