# Pyrrlanta (Fabric 1.19.2 port)

A Fabric port of [Pyrrlanta](https://github.com/CuteNikoo/pyrrlanta) (the
NeoForge 1.21.1 mod) targeting Minecraft 1.19.2. This repo starts as a bare
skeleton; the tribe/land-claiming system is being ported over from the
NeoForge version feature by feature.

## Requirements

- JDK 17 (Gradle will auto-download one via its toolchain support if you
  don't have it — you don't need to install it manually).
- Any Java IDE with Gradle support. IntelliJ IDEA is the most common choice
  for Fabric development.

## Getting started

Clone the repo, then open it in your IDE as a Gradle project (or run the
commands below from the repo root).

```bash
# Run the game as a client, with the mod loaded
./gradlew runClient

# Run a dedicated server, with the mod loaded
./gradlew runServer

# Just build the mod jar (output in build/libs/)
./gradlew build
```

On Windows use `gradlew.bat` instead of `./gradlew`.

If your IDE is missing dependencies or something looks stale, try:

```bash
./gradlew --refresh-dependencies
./gradlew clean
```

## Project layout

- `src/main/java/com/pyrrlanta/pyrrlanta/` — common (server + client) mod
  source code.
  - `Pyrrlanta.java` — main mod entry point (`ModInitializer`).
- `src/client/java/com/pyrrlanta/pyrrlanta/client/` — client-only source
  code (Fabric Loom splits this into its own source set).
  - `PyrrlantaClient.java` — client entry point (`ClientModInitializer`).
- `src/main/resources/assets/pyrrlanta/` — lang files, textures, models, etc.
- `src/main/resources/fabric.mod.json` — mod metadata.
- `gradle.properties` — mod version, maven group, and Minecraft/Fabric
  Loader/Loom/Fabric API versions live here.

## Relationship to the NeoForge version

This is a separate codebase from the main
[Pyrrlanta](https://github.com/CuteNikoo/pyrrlanta) NeoForge mod, not a
shared/multi-loader project. Features are ported over manually, adapted to
Fabric's APIs (Fabric API event callbacks instead of NeoForge's event bus,
`PersistentState` instead of `SavedData`, etc.), so behavior may drift
slightly between the two versions.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branch/PR workflow.

## License

MIT — see [LICENSE](LICENSE).
