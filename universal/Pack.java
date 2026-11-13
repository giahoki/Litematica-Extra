import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

class Pack {
    static final String PACKAGE = "me/giahoki/litematicaextra/";
    static final String MIXINS = "litematica-extra.mixins.json";

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path bootstrap = Path.of(args[1]);
        try (var jar = new JarOutputStream(Files.newOutputStream(Path.of(args[2])))) {
            try (var files = Files.walk(bootstrap.resolve("me/giahoki/universal"))) {
                for (Path file : files.filter(Files::isRegularFile).toList())
                    write(jar, bootstrap.relativize(file).toString().replace('\\','/'), Files.readAllBytes(file));
            }
            StringJoiner mixins = new StringJoiner(", ");
            StringJoiner versions = new StringJoiner(", ");
            String metadata = null;
            for (int patch = 0; patch <= 11; patch++) {
                String version = patch == 0 ? "1.21" : "1.21." + patch;
                Path input = Path.of(args[3 + patch]);
                try (var variant = new JarFile(input.toFile())) {
                    String prefix = PACKAGE + "v" + patch + "/";
                    var remapper = new Remapper() {
                        @Override public String map(String name) {
                            return name.startsWith(PACKAGE) ? prefix + name.substring(PACKAGE.length()) : name;
                        }
                    };
                    String configName = "litematica-extra-v" + patch + ".mixins.json";
                    String config = read(variant, MIXINS).replace(PACKAGE.replace('/', '.') + "mixin", prefix.replace('/', '.') + "mixin")
                            .replace("\"client\"", "\"plugin\": \"me.giahoki.universal.Selector$V" + patch + "\", \"universalMixins\"");
                    // The plugin reads this list and supplies it only on its matching Minecraft version.
                    write(jar, configName, config.getBytes(StandardCharsets.UTF_8));
                    mixins.add("\"" + configName + "\"");
                    versions.add("\"" + version + "\"");
                    var entries = variant.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith(PACKAGE) && name.endsWith(".class")) {
                            var writer = new ClassWriter(0);
                            new ClassReader(variant.getInputStream(entry).readAllBytes())
                                    .accept(new ClassRemapper(writer, remapper), 0);
                            write(jar, prefix + name.substring(PACKAGE.length()), writer.toByteArray());
                        } else if (patch == 8 && !entry.isDirectory() && name.startsWith("assets/")) {
                            write(jar, name, variant.getInputStream(entry).readAllBytes());
                        }
                    }
                    if (patch == 8) metadata = read(variant, "fabric.mod.json");
                }
            }
            metadata = Objects.requireNonNull(metadata)
                    .replace("me.giahoki.litematicaextra.ExtraClient", "me.giahoki.universal.Entry")
                    .replace("\"" + MIXINS + "\"", mixins.toString())
                    .replace("\"minecraft\": \"1.21.8\"", "\"minecraft\": [" + versions + "]")
                    .replaceAll("(\"(?:litematica|malilib)\": )\"[^\"]+\"", "$1\"*\"")
                    .replaceAll("(\"fabricloader\": )\"[^\"]+\"", "$1\">=0.19.3\"");
            write(jar, "fabric.mod.json", metadata.getBytes(StandardCharsets.UTF_8));
            write(jar, "LICENSE", Files.readAllBytes(root.resolve("LICENSE")));
        }
    }

    static String read(JarFile jar, String name) throws Exception {
        try (var in = jar.getInputStream(jar.getJarEntry(name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void write(JarOutputStream jar, String name, byte[] content) throws Exception {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content);
        jar.closeEntry();
    }
}
