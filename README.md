# Litematica Extra

<img src="src/main/resources/assets/litematica_extra/icon.png" alt="" width="72" align="right">

Edit Litematica schematics in real Minecraft Creative worlds, preview them in 3D, and position their holograms with on-screen controls.

**Minecraft 1.21–1.21.11 · Fabric · MIT**

## Installation

Install **Java 21**, **Fabric Loader 0.19.3 or newer**, and the versions of **Fabric API**, **Litematica**, and **MaLiLib** for your Minecraft release. Put the universal JAR in your client profile's `mods` folder. The same JAR selects the appropriate implementation for each supported Minecraft release.

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Litematica](https://modrinth.com/mod/litematica)
- [MaLiLib](https://modrinth.com/mod/malilib)

## Editing schematics

1. Open **Litematica extra** from the Minecraft title screen.
2. Select a `.litematic` file and load it in Creative, or choose **Создать лайтматику** to start with a blank world. **Открыть папку схем** opens your schematics folder.
3. Build with normal Minecraft blocks and the Creative inventory. In these editing worlds, gravity blocks and liquids stay in place.
4. Open the pause menu and choose **Сохранить .litematic** or **Сохранить и выйти**, then enter the filename. Minecraft's standard exit saves the world only.

A blank world starts with a protected sea lantern. It disappears when you place the first block, returns when the build is empty, and stays out of the exported schematic. Temporary worlds are hidden from the normal world list and are deleted after a successful **Save and Exit** export. To find a retained temporary world, search the singleplayer list for `litematica-extra-`.

## Placement and preview

- In Litematica's schematic browser, choose **Размещение** to position a hologram with a detached camera. Press **Alt** to show the cursor, then drag the axis arrows to move it.
- Press **R** to switch between the arrows and the rotation ring. Drag the ring to rotate by 90° around the center of the build.
- Open **3D Editor** from the Litematica menu to orbit the model, replace or remove blocks, and save a copy.

Some add-on controls currently use Russian labels. The editor reads schematics up to 32 MiB and 2 million cells. Creative exports also have a 2-million-cell bounding-volume limit.

## Build from source

Keep JDK 21 local to the checkout. On Windows, place it in `.jdk/21` with `bin/java.exe` under that directory. The Windows Gradle Wrapper uses it automatically.

| Command | Result |
| --- | --- |
| `.gradlew.bat build` | Builds and tests all 12 targets, then packages the universal JAR in `build/universal/` |
| `.gradlew.bat "-PminecraftVersion=1.21.8" build` | Builds only the selected Minecraft target |
| `.gradlew.bat runClient` | Launches the 1.21.8 development client |
| `.gradlew.bat -PtestUniversal runUniversalClient` | Launches the packaged universal JAR in an isolated 1.21.8 profile |

Shared code lives in `src/main`. Version-specific adapters live in `src/compat`. The dependency matrix is in `build.gradle`.

## License

[MIT](LICENSE) · Maintained by [giahoki](https://github.com/giahoki)
