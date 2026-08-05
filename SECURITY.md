# Security Policy

## Reporting

Do not open a public issue containing credentials, customer data, exploit details, or an unpatched high-impact vulnerability. Use GitHub private vulnerability reporting when enabled, or contact the repository owner through a private channel.

Include affected revision, impact, minimal reproduction, and any evidence that can be safely shared. Redact tokens, passwords, personal data, internal addresses, and production logs.

## Response boundaries

- Exposed credentials are revoked or rotated before code cleanup is considered complete.
- Security fixes use an isolated branch and pass the normal quality and security gates.
- Authentication, authorization, tenant isolation, privacy, irreversible data operations, and production changes require human approval.
- Automated agents may investigate sanitized evidence and prepare a PR, but may not retrieve secrets, deploy, merge, or mutate production.

See `AGENTS.md` and `docs/steering/agent-governance.md` for agent-specific controls.
