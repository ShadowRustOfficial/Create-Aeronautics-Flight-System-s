# Flight Computer 0.2.0

Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21.

Target integrations: Create 6.0.10, Create Aeronautics 1.3.0, Sable 2.0.3, Create Propulsion: Simulated 1.1.5, Xaero World Map 1.44.2, Xaero Minimap 26.4.2, Aeroclaim 0.9.3, Open Parties and Claims 0.29.3.

Implemented: Flight Computer block/item, persistent server state, four-tab console shell, six-vector link configuration, named channels, test output commands, flight modes, route/waypoint model, PID settings, diagnostics state, client/server GUI command architecture, and isolated Create Redstone Link integration boundary.

Build note: public search did not expose the exact CurseForge file ID for Create 6.0.10. Replace `create_file_id` in `gradle.properties` with the 6.0.10 Curse Maven file ID, or provide the jar locally. Dependency download was unavailable in this environment, so Gradle could not be executed here.


### Build fix in this revision
This revision targets NeoForge **21.1.233** and explicitly includes Mojang's library Maven repository so NeoForge can resolve `com.mojang:logging:1.1.1`.
