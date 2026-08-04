# Delivery contract: private helper rename

## Intent

Correct the spelling of the private `SkuNormalizer` helper from `normaliseSku` to `normalizeSku` while preserving the public API and all observable behavior.

This is a narrow, low-risk refactor. It does not require new feature-specification artifacts unless repository policy explicitly requires them.

## Scope

- Rename the private helper declaration from `normaliseSku` to `normalizeSku`.
- Update every internal call site in `SkuNormalizer`.
- Update the focused unit test’s names, descriptions, or comments if they refer to `normaliseSku`.
- Continue testing the behavior through the public `normalize` method.

## Acceptance criteria

1. `SkuNormalizer` declares the private helper as `normalizeSku`.
2. No executable production or focused-test reference to `normaliseSku` remains.
3. The public `normalize` method retains the same name, visibility, parameters, return type, exceptions or error contract, and side effects.
4. For all inputs covered by the focused unit test, `normalize` produces the same outputs and externally observable effects as before.
5. The focused unit test exercises the helper only through `normalize`; it does not expose, reflectively invoke, or weaken the visibility of the private helper.
6. The focused test passes.
7. The affected module compiles successfully.
8. The final diff contains only the private rename and necessary test-reference updates.

## Explicit exclusions

- No normalization algorithm changes.
- No new validation or supported input cases.
- No error-message, ordering, state, or side-effect changes.
- No rename of the public `normalize` method.
- No compatibility alias for the private helper.
- No unrelated spelling cleanup, code restructuring, or file movement.

## Verification evidence

Completion should include:

- A search showing that `normaliseSku` is absent from the affected production and focused-test code.
- A passing focused unit-test command for `SkuNormalizer`.
- A successful compile or test task for the containing module.
- Diff inspection confirming that executable changes are identifier-only and that the public surface is unchanged.
- Disclosure of any skipped checks or unrelated pre-existing failures.

## Definition of done

The change is complete when the private rename is consistent, the focused behavior test and module compilation pass, the public API is unchanged, no observable behavior differs, unrelated changes are absent, and verification evidence is reported.

No files were changed while describing this contract.
