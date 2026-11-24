# rw-plugin-oz-mail
<!-- codemap-id: rw-plugin-oz-mail -->

## Purpose

Mailboxes, recoverable attachment custody and trusted plugin-originated delivery. [Descriptor](../src/resources/plugin.yml) names [`OZMail`](../src/de/omegazirkel/risingworld/OZMail.java); [`OZMailRuntime`](../src/de/omegazirkel/risingworld/OZMailRuntime.java) handles lifecycle and dispatch.

## Modules

- [Mail delivery and custody](modules/delivery.md).
- [Mailbox UI and capacity](modules/ui.md).

## Build and tests

[Maven](../pom.xml) builds against the Rising World API and OZ Tools. Explore [tests](../tests/de/omegazirkel/risingworld/mail/) and follow [repository instructions](../AGENTS.md) for validation.

## Dependencies

- `rw-plugin-oz-tools/shared-api` — build and shared runtime infrastructure. [Build configuration](../pom.xml)
