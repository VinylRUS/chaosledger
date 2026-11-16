# Contributing to ChaosLedger

Thanks for taking the time to contribute! 🎉 ChaosLedger is an authoritative server-side
mod for Minecraft 26.3 on Fabric, and we want every contribution to keep the ledger
**strict, transactional and trustworthy**.

This document describes how to set up your environment, the conventions we follow and
how to get your change merged.

## Code of Conduct

By participating you agree to uphold the [Code of Conduct](CODE_OF_CONDUCT.md). Be kind,
be precise, assume good intent.

## Quick start

```bash
git clone https://github.com/OWNER/chaosledger.git
cd chaosledger

# JDK 25 is required (the mod targets Java 25).
java -version

# Build the mod + run tests. The gradle-wrapper.jar is committed, so no system
# Gradle install is needed.
./gradlew build
```

The first build downloads Minecraft, Mojang official mappings (Mojmap) and Fabric API
through Loom — be patient and stay online. The resulting jar is in
`build/libs/chaosledger-<version>.jar`.

## Repository layout

See [README.md](README.md) for the full tree. The short version:

- `src/main/java/dev/chaosledger/chaosledger/economy/` — accounts, money, transactions, market, prices.
- `src/main/java/dev/chaosledger/chaosledger/quest/` — quest definitions and the state machine.
- `src/main/java/dev/chaosledger/chaosledger/reward/` — rewards + item delivery adapter.
- `src/main/java/dev/chaosledger/chaosledger/audit/` — audit log.
- `src/main/java/dev/chaosledger/chaosledger/storage/` — SQLite + repositories.
- `src/main/java/dev/chaosledger/chaosledger/http/` — embedded HTTP API.
- `src/main/java/dev/chaosledger/chaosledger/command/` — Brigadier `/cl` commands.
- `src/main/java/dev/chaosledger/chaosledger/gui/` — chest GUI.
- `src/main/java/dev/chaosledger/chaosledger/item/` — items + Data Components.

## Conventions

### Branches & commits

- Branch from `main` (or `develop` if it exists). Name branches `feat/...`,
  `fix/...`, `docs/...`, `chore/...`, `refactor/...`.
- Use [Conventional Commits](https://www.conventionalcommits.org/):
  `feat(market): decay supply every tick`, `fix(economy): reject negative payments`,
  `docs: clarify HTTP auth`.
- Keep commits atomic and reviewable. Squash noisy WIP commits before opening the PR.

### Code style

- Java 25 features are encouraged: `record` for value types, `sealed` for closed
  hierarchies, pattern matching in `switch`.
- Immutability first: domain types (`Money`, `Account`, `Transaction`, `Quest`) are
  records or otherwise immutable. Mutations return new instances.
- Never thread raw `double` amounts through the API — always wrap them in `Money`
  so the currency travels with the value.
- Every mutation that changes persisted state (balances, progress, market rows) must
  commit in the same database transaction as the matching ledger / audit row.
- Public classes get a Javadoc paragraph explaining intent, not just restating the name.
- Match the surrounding density of comments; do not over-comment obvious code.

### Tests

- New engine logic should come with a JUnit test under `src/test/java/...` that drives
  it through the public API and asserts the persisted outcome.
- Storage tests use an in-memory SQLite file (`:memory:`) created in `@BeforeEach`.
- Run `./gradlew test` before pushing.

### Mappings

The mod uses **Mojang's official names (Mojmap)** for Minecraft 26.3, consumed natively
by Loom's "debof" (de-obfuscated) code path. There is **NO `mappings` line in
`build.gradle`** — and there must not be one on 26.x.

Here is why. Since 1.21.11 snapshots (release-time cutoff 2025-11-01) the game ships
un-obfuscated, so Mojang's class/member names are the canonical standard. Yarn is
deprecated for the 26.x era, and Parchment is not needed (no obfuscation to layer over).
Crucially, **Mojang no longer publishes `client_mappings` ProGuard files** in the
version manifest for 26.x — the 26.3 manifest has only `client` and `server` jars.
So `mappings loom.officialMojangMappings()` would throw
`Failed to find official mojang mappings for 26.3`, and `loom.layered { ... }` with
Parchment is both broken (no client_mappings) and unnecessary.

Instead, Loom's debof path is activated by the Gradle property
`fabric.loom.disableObfuscation=true` in `gradle.properties`. That flips
`disableObfuscation()` to true, and `CompileConfiguration.provideMinecraft()` then
skips the MAPPINGS configuration entirely (it consults the optional ANNOTATIONS
config instead, which is empty and fine). Loom reads Mojang names straight out of
the un-obfuscated client.jar. Do not remove that property, and do not add a
`mappings` line to `build.gradle` — both will break the build.

**Build toolchain:** Gradle 9.8.2 + Loom 1.19-SNAPSHOT + JDK 25 +
`fabric.loom.disableObfuscation=true`. Gradle 8.x cannot read Java 25 class files
and crashes with `Unsupported class file major version 69` — do not downgrade the
wrapper below 9.4.

When porting code from an older Yarn-based mod, the common renames are:

| Yarn | Mojmap |
| --- | --- |
| `net.minecraft.server.network.ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.ResourceLocation` |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` |
| `net.minecraft.util.Formatting` | `net.minecraft.ChatFormatting` |
| `net.minecraft.screen.ScreenHandler` | `net.minecraft.world.inventory.AbstractContainerMenu` |
| `net.minecraft.screen.ScreenHandlerType` | `net.minecraft.world.inventory.MenuType` |
| `net.minecraft.inventory.SimpleInventory` | `net.minecraft.world.SimpleContainer` |
| `net.minecraft.server.command.ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` |
| `net.minecraft.item.Item` / `Items` / `ItemStack` | `net.minecraft.world.item.*` |
| `net.minecraft.registry.Registries` | `net.minecraft.core.registries.BuiltInRegistries` |
| `net.minecraft.component.ComponentType` | `net.minecraft.core.component.DataComponentType` |
| `net.minecraft.network.codec.PacketCodecs` | `net.minecraft.network.codec.StreamCodecs` |

If a name is ambiguous, **say so** in the PR description rather than guessing —
we will verify against the decompiled game code before merging.

## Pull requests

1. Open the PR against `main` (or `develop` if it exists).
2. Fill in the [PR template](.github/PULL_REQUEST_TEMPLATE.md).
3. Make sure `./gradlew build` is green on your branch. CI will re-run it.
4. Request a review from a maintainer. Be responsive to feedback.
5. Do not force-push after review unless asked; it makes the conversation hard to follow.

## Releases

Releases are **tag-driven**. To cut a new release:

```bash
git tag v0.2.0
git push origin v0.2.0
```

The [`release`](.github/workflows/release.yml) workflow then:

1. Checks out the tag.
2. Builds the mod with `RELEASE_VERSION=v0.2.0` (so the jar and `fabric.mod.json`
   carry the tag, not the placeholder in `gradle.properties`).
3. Creates a GitHub Release named `ChaosLedger v0.2.0` with the jar and an
   auto-generated changelog.

Pre-release builds can be triggered manually from the *Actions* tab with a `tag` and
`prerelease=true` input.

## Reporting security issues

Do **not** open a public issue for security vulnerabilities. Email the maintainers
privately at `security@example.com` (replace with the real address). We will respond
within 72 hours and coordinate a fix + disclosure.

## Licensing

By contributing you agree that your changes will be licensed under the project's
[MIT license](LICENSE).
