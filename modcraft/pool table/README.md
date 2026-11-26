# Pool Table — Fabric 26.2 multiplayer build

The original `pooltable-fabric-26-1.8.0.jar` is kept unchanged. The source in
`src/main/java` was reconstructed from that MIT-licensed artifact because no
original source project was available; models, textures, sounds, recipes and
other assets are copied from the original JAR.

## Changes

- Expanded table state and seat actions to five players, with persisted seat
  and match-format state.
- Kept the original three-player Cutthroat ball groups and added individual
  four- and five-player Cutthroat groups.
- Added Individual, 1v1, 2v2 and 2v3 format selection for eligible modes.
  Cutthroat remains individual; the other existing modes support teams.
- Made team turns rotate between sides and rotate teammates, and share group
  assignments, points and match wins. Server-side action validation remains
  authoritative; duplicate player seats are rejected and stale player seats
  can be reclaimed after disconnect.
- Expanded the existing table screen and HUD to show five seats and team
  membership.

## Build and automated tests

Run Gradle with Java 21 or another JVM supported by Gradle 9.4.1; the configured
Java 27 toolchain compiles the mod with Java 25 language/API compatibility:

```sh
./gradlew build
```

The unit tests cover supported formats, 2v2/2v3 turn rotation, Cutthroat ball
ownership, team rule outcomes, shared scoring, and save/reconnect behavior.
The custom block-entity render state must be initialized with Minecraft 26.2's
base state before submission; without its block-entity type, Minecraft skips
the renderer's submit phase. This was fixed for the table, lamp and cue rack.
The user confirmed the balls render correctly after installing the updated
server-synchronized JAR. Multiplayer synchronization and disconnect/reconnect
behavior have not been independently stress-tested with multiple clients.

The compiled mod is written to
`build/libs/pooltable-fabric-26-1.8.0-multiplayer+26.2.jar`; a copy of the
verified release artifact is also kept at the project root. Its metadata
targets Minecraft 26.2 only.

## Better Block Entities crash compatibility

The separate `bbe-compat` project builds a client-only add-on for the
`blockEntityExt == null` crash in Better Block Entities 1.3.8-beta.1 on
Minecraft 26.2. It leaves BBE enabled and falls back to BBE's normal renderer
for entries without an associated block entity. Install
`bbe-compat/build/libs/pooltable-bbe-compat-1.0.0+26.2.jar` in the launcher's
instance-level `mods` folder (not the AutoModpack-managed server pack), then
restart Minecraft. This avoids changing the server's mod list or its synced
Pool Table JAR.

To rebuild the add-on, run `../gradlew build` from the `bbe-compat` directory
with Gradle running on Java 21.
