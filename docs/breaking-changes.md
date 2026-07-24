# Breaking Changes

Breaking changes to Zolt's CLI and configuration, newest first. Each entry names
the old behavior, the new behavior, and how to migrate.

## `zolt update` repurposed for dependency updates

- **Old behavior:** `zolt update` updated the installed Zolt binary — a redundant
  alias of `zolt self update`.
- **New behavior:** `zolt update` updates dependency, platform, and version-alias
  versions in `zolt.toml`. It never touches the Zolt binary.
- **Migration:** to update the Zolt binary, run `zolt self update` (unchanged and
  canonical). Scripts or habits that ran `zolt update` for self-update must switch
  to `zolt self update`.

See [Dependency Updates](../USAGE.md#dependency-updates) for the repurposed command.
