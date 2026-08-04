# Registration Apostrophe Bugfix Plan

## Status and scope

The failure is reported but not yet reproduced. SQL interpolation is a hypothesis, not a root-cause finding.

Reported current behavior: an otherwise valid registration fails when the display name contains an apostrophe, such as `O'Connor`.

Expected behavior: if apostrophes are permitted by the existing display-name rules, registration succeeds with the same API contract and side effects as a valid normal name, preserving the name exactly.

Unchanged behavior:

- Normal display names behave exactly as before.
- Email validation retains its accepted/rejected inputs, validation precedence, status, error response, and no-write behavior.
- Password validation retains the same behavior and contract.
- Existing display-name constraints other than apostrophe handling remain unchanged.
- No schema or public API change is intended.

First confirm that the accepted display-name specification permits apostrophes. If it explicitly excludes them, this is a behavior change rather than a bugfix.

## Work plan

1. **Reproduce the defect**
   - Identify the registration endpoint and trace its request, validation, service, and persistence path.
   - Execute a valid control registration and an equivalent isolated registration using `O'Connor`.
   - Capture the HTTP response, exception and stack trace, failing boundary, and persistence outcome without logging passwords or other secrets.
   - Add the narrowest stable API or integration regression test and run it before changing production code.
   - Confirm the test fails because of the reported condition, not fixture reuse, email uniqueness, environment setup, or another unrelated cause.

2. **Establish causal evidence**
   - Trace the reproduced failure to the smallest responsible component.
   - Add a focused lower-level test only if necessary to connect the API failure to that component.
   - Record SQL interpolation as the root cause only if evidence shows the apostrophe reaches a dynamically constructed query and causes the observed database/query failure while the control request succeeds.
   - Otherwise, repair the actual demonstrated cause.

3. **Implement the smallest repair**
   - Change only the proven causal boundary.
   - If interpolation is confirmed, replace it with the project’s normal parameter-binding or ORM mechanism. Do not manually escape apostrophes or globally sanitize display names.
   - Inspect only the affected statement or narrowly shared helper for adjacent user-controlled interpolation; avoid a repository-wide refactor without evidence.
   - Do not modify email/password validators, validation ordering, unrelated name normalization, request/response models, or database schema.

4. **Verify behavior**

   | Scenario | Required result |
   |---|---|
   | Valid normal name, email, and password | Existing success status, response, and persistence behavior |
   | `O'Connor` with valid email and password | Same success contract; exact name stored and returned |
   | Invalid email | Existing validation status, error contract, precedence, and no-write behavior |
   | Invalid password | Existing validation status, error contract, precedence, and no-write behavior |

   Run the new test red-before/green-after, any focused causal test, existing name/email/password validation tests, and the affected registration integration suite. Broaden testing only if the repair touches shared infrastructure.

## Durable artifacts

For this narrow repair, retain only:

- one API or integration regression test;
- an additional causal test only if it materially localizes the failure;
- the minimal implementation change;
- one concise bugfix record containing the reported symptom, reproduction evidence, proven root cause, expected and unchanged behavior, repair boundary, and executed verification.

No separate design or task documents are justified unless investigation reveals a cross-layer, shared-infrastructure, schema, or security change. No requirement delta is needed if current specifications already allow apostrophes; if the rule is unstated, add only the narrow display-name expectation.

## Completion criteria

The repair is complete only when the original failure or equivalent causal condition was demonstrated before the change, the root cause is evidence-backed, apostrophe-containing names round-trip exactly, normal names and email/password validation remain unchanged, rejected registrations create no partial data, and all relevant executed or skipped checks are reported.
