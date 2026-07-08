# Contributing to Pyrrlanta (Fabric 1.19.2 port)

## Workflow

1. Clone the repo (don't work off a fork unless you're an outside
   contributor without write access).
2. Create a branch off `main` for your change:
   ```bash
   git checkout -b your-name/short-description
   ```
3. Commit your changes with clear messages.
4. Push your branch and open a pull request against `main`.
5. At least one other person should review before merging, when possible.
6. The `Build` GitHub Actions workflow runs `./gradlew build` on every push
   and PR — make sure it's green before merging.

`main` is the shared branch. Avoid pushing directly to it for anything
non-trivial — use a branch + PR so people don't step on each other's changes.

## Avoiding merge conflicts

- Keep `gradle.properties` (mod version, Loader/Loom/Fabric API versions)
  changes to their own small PRs — everyone touches this file rarely, but
  conflicts here are annoying.
- If you're adding a new block/item/feature, put it in its own Java file
  rather than piling onto `Pyrrlanta.java`, and register it from there. This
  keeps the main class as a small, stable set of registration calls instead
  of a merge-conflict magnet.
- Two people editing the same lang file (`en_us.json`) at once is a common
  source of conflicts — pull before you start, and coordinate in chat if
  you're both adding translations at the same time.
- Common (server + client) code goes in `src/main/java`; client-only code
  (rendering, screens, etc.) goes in `src/client/java` — Fabric Loom keeps
  these as separate source sets, so mixing them up will break the build.

## Code style

- Follow the existing formatting in the template (Fabric's standard example
  mod conventions — tab indents, standard Java package structure).
- Mod id is `pyrrlanta`; keep resource/registry names in that namespace.
- This is a from-scratch port of features from the
  [NeoForge version](https://github.com/CuteNikoo/pyrrlanta), not a direct
  code copy — Fabric's APIs differ (event callbacks vs. an event bus,
  `PersistentState` vs. `SavedData`, no `ModConfigSpec` equivalent, etc.), so
  expect to adapt logic rather than paste it.

## Local testing

Run `./gradlew runClient` to launch the game with your changes and check
them in-game before opening a PR.
