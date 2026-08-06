# Flight Computer 0.2.1 build notes

Target environment:
- Minecraft 1.21.1
- NeoForge 21.1.233
- Java 21
- Create 6.0.10 (Maven build 223, compile-only API)

This revision uses the official NeoForge 1.21.1 NeoGradle MDK approach rather than ModDevGradle. This avoids the missing `net.neoforged:minecraft-dependencies:1.21.1` resolution problem encountered with the previous build setup.

Create is resolved from the official Create Maven repository instead of being downloaded by a custom Gradle task.

Run:
  gradlew.bat clean
  gradlew.bat build

Or:
  gradlew.bat runClient

If the project was previously opened with the old build, use a fresh checkout/extraction and let Gradle re-import it.
