# Changelog

All notable changes to IDSheet Studio are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). The release workflow publishes the section that
matches a pushed tag as that release's notes.

## [Unreleased]

## [1.0.0] - 2026-10-29

### Added
- Photo import from the system photo picker (multiple photos at once; JPEG, PNG, WebP, HEIC).
- Background removal through the user's own remove.bg API key, with a pure white (or custom
  colour) background. The key is encrypted with the Android Keystore.
- Crop editor with pinch-to-zoom, drag to reposition, twist or 90° rotation, straighten slider
  and a passport face guide.
- 28 photo-size presets (passport, visa, ID, stamp and country specific) plus custom sizes in
  millimetres, centimetres or inches.
- Individual size and quantity for every photo.
- Paper presets A4, A5, Letter, Legal, 4 × 6 in and 5 × 7 in, custom paper, portrait and
  landscape, exact margins and photo spacing.
- Optional page border and photo borders with thickness, colour, style (solid, dashed, dotted)
  and inset.
- Smart multi-page layout (neat rows or maximum fit, chosen automatically), optional rotation,
  manual drag-to-reposition and crop marks or cut lines.
- Print-ready multi-page PDF with exact fractional page sizes, 300–600 DPI images, a read-back
  verification of every page and photo size, preview, share, save and Android printing.
- Offline projects: save, reopen, edit, rename, duplicate, delete and export.
- Automatic update popup when a new GitHub release is published (checked on every app start,
  hourly at most, and every 6 hours in the background with a tap-to-update notification), with
  changelog, Update, Later and Skip version, and a verified in-app install (SHA-256 checksum,
  package name, version and signing certificate).
- Material 3 design with dynamic colour, light and dark themes and expressive motion.

[Unreleased]: https://github.com/riyadmondol2006/IDSheet-Studio/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/riyadmondol2006/IDSheet-Studio/releases/tag/v1.0.0
