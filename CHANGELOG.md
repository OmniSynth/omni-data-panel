# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
for release tags (`v*`).

## [Unreleased]

### Added

- Apache License 2.0 (`LICENSE`, `NOTICE`) and community docs (`CONTRIBUTING`, `SECURITY`, `CODE_OF_CONDUCT`)
- Table-level MySQL `COMMENT` for business tables (`V21__table_comments.sql`)
- Embed JWT locked dashboard parameters (`POST /api/embed/tokens` → `parameters` claim)
- Analyst self-service email subscriptions (`subscription:manage`, product `/subscriptions`)
- Export audit log (`bi_export_audit`) and admin UI

### Changed

- Documentation synced for embed locked params, subscriptions, and export audits

## [0.1.0] - 2025

### Added

- Initial public release: single-tenant self-hosted BI (models, charts, dashboards, SQL, ACL, OIDC, schedules, Compose/Release packaging)

[Unreleased]: https://github.com/OmniSynth/omni-data-panel/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/OmniSynth/omni-data-panel/releases/tag/v0.1.0
