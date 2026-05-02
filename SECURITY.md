# Security Policy

## Supported Versions

IoCraft follows an active-support model focused on the latest stable line.

- `1.x` -> supported
- `0.x` -> unsupported

## Reporting a Vulnerability

If you discover a security issue, please report it privately and avoid public disclosure until a fix is available.

Preferred contact:

- Open a private security report in the repository (if enabled), or
- Contact the maintainer directly (Jose Escorcia / Curius).

Please include:

- affected version (`mod_version`)
- environment (Minecraft/Forge/OS)
- reproduction steps
- expected vs actual behavior
- impact assessment
- proof-of-concept (if available)

## Maintainer Best-Effort Goals

We aim to acknowledge reports within 7 days, on a best-effort basis.

Additional best-effort goals:

- validate and reproduce the issue
- prepare and test a fix
- publish a patched version and mitigation notes

## Security Scope Notes

IoCraft is designed for LAN/local usage by default.

- `ws://` transport does not provide encryption by itself.
- Authentication (`HMAC + nonce`) provides integrity/authentication, not confidentiality.
- For untrusted networks, use an external secure transport layer (for example: `wss://` gateway, VPN, segmented LAN, firewall allowlist).

## Responsible Disclosure

Do not publish exploit details, proof-of-concepts, or attack instructions before maintainers have had a reasonable chance to release a fix.

