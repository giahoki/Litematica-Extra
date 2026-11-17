# Litematica Extra

<img src="src/main/resources/assets/litematica_extra/icon.png" alt="" width="72" align="right">

Edit Litematica schematics in real Minecraft Creative worlds, preview them in 3D, and position their holograms with on-screen controls.

**Minecraft 1.21–1.21.11 · Fabric · MIT**

## Downloads

| Mod version | Minecraft versions | Loader | File |
| --- | --- | --- | --- |
| 0.7.1 | 1.21–1.21.11 (all 12 releases) | Fabric 0.19.3+ | [Download universal JAR](https://github.com/giahoki/Litematica-Extra/releases/download/0.7.1-1.21/litematica-extra-0.7.1-universal.jar) |

## Installation

Install **Java 21**, **Fabric Loader 0.19.3 or newer**, and the versions of **Fabric API**, **Litematica**, and **MaLiLib** for your Minecraft release. Put the universal JAR in your client profile's `mods` folder. The same JAR selects the appropriate implementation for each supported Minecraft release.

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Litematica](https://modrinth.com/mod/litematica)
- [MaLiLib](https://modrinth.com/mod/malilib)

## Editing schematics

1. Open **Litematica extra** from the Minecraft title screen.
2. Select a `.litematic` file and load it in Creative, or choose **Create Schematic** to start with a blank world. **Open Schematics Folder** opens your schematics folder.
3. Build with normal Minecraft blocks and the Creative inventory. Open **Esc → Physics** to turn water and lava flow or falling blocks on and off for the current editing world. Your settings are saved with that world.
4. Open the pause menu and choose **Save .litematic** or **Save and Exit**, then enter the filename. Minecraft's standard exit saves the world only.

A blank world starts with a protected sea lantern. It disappears when you place the first block, returns when the build is empty, and stays out of the exported schematic. Temporary worlds are hidden from the normal world list and are deleted after a successful **Save and Exit** export. To find a retained temporary world, search the singleplayer list for `litematica-extra-`.

## Placement and preview

- In Litematica's schematic browser, choose **Placement** to position a hologram with a detached camera. Press **Alt** to show the cursor, then drag the axis arrows to move it.
- Press **R** to rotate the placement 90° clockwise around its center.
- Open **3D Editor** from the Litematica menu to orbit the model, replace or remove blocks, and save a copy.

The interface follows the Minecraft language setting. English and Russian cover the full interface. The other languages shipped by Litematica have translated main controls and use English for the remaining text. The editor reads schematics up to 32 MiB and 2 million cells. Creative exports also have a 2-million-cell bounding-volume limit.

## Build from source

Keep JDK 21 local to the checkout. On Windows, place it in `.jdk/21` with `bin/java.exe` under that directory. The Windows Gradle Wrapper uses it automatically.

| Command | Result |
| --- | --- |
| `.\gradlew.bat` or `.\gradlew.bat build` | Builds and tests all 12 targets, then packages the universal JAR in `build/universal/` |
| `.\gradlew.bat assemble` | Packages the universal JAR in `build/universal/` |
| `.gradlew.bat "-PminecraftVersion=1.21.8" build` | Builds only the selected Minecraft target |
| `.gradlew.bat runClient` | Launches the 1.21.8 development client |
| `.gradlew.bat -PtestUniversal runUniversalClient` | Launches the packaged universal JAR in an isolated 1.21.8 profile |

Shared code lives in `src/main`. Version-specific adapters live in `src/compat`. The dependency matrix is in `build.gradle`.

## License

[MIT](LICENSE) · Maintained by [giahoki](https://github.com/giahoki)
