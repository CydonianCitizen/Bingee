# Security Policy

## Supported versions

Bingee is unreleased. Only the current default branch receives security fixes; there are no supported release versions yet.

## Reporting a vulnerability

Use GitHub''s private vulnerability reporting feature for this repository when it is available. Do not disclose a suspected vulnerability in a public issue, discussion, pull request, or commit.

If private vulnerability reporting is unavailable, contact the maintainers through a private channel already published on the repository owner''s GitHub profile. Do not include exploit details in a public request for contact. This project does not publish a dedicated security email address yet.

Include:

- affected revision and environment;
- impact and attack scenario;
- reproduction steps or a minimal proof of concept;
- suggested mitigation, if known.

Remove personal data, production credentials, and unrelated secrets from reports.

## Secrets

API keys, access tokens, signing keys, keystores, passwords, local SDK paths, and real user data must never be committed. Revoke and replace any exposed credential; deleting it from a later commit is not sufficient.

TMDB credential reports must never include the raw token, Authorization header, encrypted credential file, decrypted values, or screenshots with a revealed field. Report only the safe credential state, HTTP category, affected revision, and reproduction steps using a clearly fake value where possible.
