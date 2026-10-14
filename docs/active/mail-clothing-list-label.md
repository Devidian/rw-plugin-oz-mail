# Mail clothing inventory labels

## Objective and constraints

- [x] Resolve the concrete clothing definition's localized name while building attachment candidates.
- [ ] Verify inventory labels before and after attachment custody; keep the stored clothing key and restore path intact.

## Affected systems and validation

Mail inventory snapshot and attachment picker. Run focused tests and Maven package, then check a clothing item in Development.

Java 25 package and 26 tests passed on 2026-10-14; Development startup was checked. The in-game clothing picker and attachment round trip remain open.

## Rollback

Restore the prior snapshot label resolution. No persisted data changes are required.
