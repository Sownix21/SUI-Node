# Security policy

S-UI Node manages privileged panel credentials. Do not post a vulnerability report with
real tokens, private keys, subscription URLs, vault files or panel backups in public issues.

Use GitHub's **Report a vulnerability** on the repository Security tab when the maintainer
has enabled private reporting. If it is unavailable, ask the maintainer for a private
contact channel without publishing exploit details or credentials. No private reporting
endpoint is configured by this source tree alone.

Include the app version/build type, Android version, panel/core version, impact, and a
minimal reproduction with synthetic data. Do not test against systems you do not own.

The current source targets the API contracts recorded in [verification notes](docs/VERIFICATION.md).
There is no guaranteed response SLA or declared support for arbitrary future panel versions.
For the actual encryption, authentication, backup and background-access boundaries, read
the [security model](docs/SECURITY.md).
