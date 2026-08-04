I can’t safely implement this public API change until its target and compatibility policy are explicit.

Current repository behavior is:

- Order responses expose `tradeStatus`, `paymentStatus`, and `fulfillmentStatus`.
- Order responses do not expose a single order-level `status`.
- Order items separately expose `items[].status`.
- The same `OrderResponse` mapping is used by create, detail, and paginated order responses.
- Existing contract tests require the order-level `status` field to be absent while preserving `items[].status`.

Please decide:

1. **What exact contract change is intended?**
   - Rename one of `tradeStatus`, `paymentStatus`, or `fulfillmentStatus`;
   - change `items[].status`;
   - add a new field;
   - or consolidate the three order-level status dimensions into one field.

   Provide the new field name, type, and value semantics. Consolidation also requires a defined mapping for every valid combination of the three existing statuses.

2. **What compatibility policy should apply?**
   - **Compatible transition:** add the replacement field, retain the old field as deprecated for an agreed period, document precedence, and remove it in a later change.
   - **Immediate breaking replacement:** remove the old field now and return only the replacement, with all consumers updated in the same release.

I recommend the compatible transition unless the API has no deployed consumers or every consumer can be coordinated atomically. Previous feature-specific decisions permitting breaking changes do not establish a general policy for this change.

Once clarified, the minimum durable work is:

- record a concise API contract delta describing the modified field, affected endpoints, value mapping, compatibility window, and removal condition;
- update controller contract tests first for create, detail, and paginated responses;
- preserve unrelated status fields, especially `items[].status`, unless explicitly included;
- implement the smallest response DTO and mapping change;
- update consumer-facing API documentation or release notes;
- run the focused controller contract test and affected Boot module tests;
- for a transition, verify old and new fields coexist with equivalent values;
- for immediate removal, verify the old field is absent and record the consumer-coordination evidence or accepted breaking-change risk.

No files were modified while these public-contract decisions remain unresolved.
