# Change Log

All notable changes to this project will be documented in this file. This
change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

### Changed

- All dependencies have been upgraded to the latest available versions.

### Fixed

- When using a custom boolean parameter type, with a default of `true` and 
  providing an override via a map source, `false` values were being ignored.
  This has now been resolved.

## [0.5.5] — 2022-02-09

### Changed

- The `json-parsing-middleware` now uses `io.logicblocks/jason` for its JSON
  parsing instead of cheshire.

## [0.5.4] — 2020-11-20

### Added

- Configuration sources now allow middleware to be applied to them allowing
  parameter keys to be transformed before they are passed to the source as well 
  as parameter values to be transformed before they are returned. See 
  `configurati.core/with-middleware` for details.
- Middleware implementations for parsing JSON parameter values and comma
  separated parameter values are now included.
- Parameters definitions now accept a `:validator` option, which can be a
  function or a `clojure.spec` spec, used to validate the parameter value on
  resolve.
- Configurations can now have transformations defined on them, allowing the
  resolved configuration map to be arbitrarily transformed before being
  returned.

### Changed

- The default type for parameters is now `:any` preventing `configurati` from
  performing any conversion on the resolved parameter value.

## [0.5.2] — 2019-10-05

### Changed

- `configurati` is now released under the `io.logicblocks` organisation.

## [0.5.1] — 2019-10-03

### Changed

- `configurati` is now released under the `logicblocks` organisation.

## [0.5.0] — 2019-10-03

### Added

- Configuration definitions can now be merged.

### Changed

- Key functions are now scoped to configuration specifications.

## [0.4.0] - 2017-06-15

Initial release

[0.4.0]: https://github.com/logicblocks/configurati/compare/0.1.0...0.4.0

[0.5.0]: https://github.com/logicblocks/configurati/compare/0.4.0...0.5.0

[0.5.1]: https://github.com/logicblocks/configurati/compare/0.5.0...0.5.1

[0.5.2]: https://github.com/logicblocks/configurati/compare/0.5.1...0.5.2

[0.5.4]: https://github.com/logicblocks/configurati/compare/0.5.2...0.5.4

[0.5.5]: https://github.com/logicblocks/configurati/compare/0.5.4...0.5.5

[Unreleased]: https://github.com/logicblocks/configurati/compare/0.5.5...HEAD
