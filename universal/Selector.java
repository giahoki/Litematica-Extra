package me.giahoki.universal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public abstract class Selector implements IMixinConfigPlugin {
    private final int patch;
    Selector(int patch) { this.patch = patch; }

    static String minecraft() {
        return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
    }

    @Override public List<String> getMixins() {
        if (!minecraft().equals(patch == 0 ? "1.21" : "1.21." + patch)) return List.of();
        String name = "/litematica-extra-v" + patch + ".mixins.json";
        try (var in = Objects.requireNonNull(Selector.class.getResourceAsStream(name), name)) {
            String config = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var list = Pattern.compile("\"universalMixins\"\\s*:\\s*\\[([^]]*)]").matcher(config);
            if (!list.find()) throw new IllegalStateException("Missing mixin list in " + name);
            return Pattern.compile("\"([\\w$]+)\"").matcher(list.group(1)).results().map(m -> m.group(1)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    public static final class V0 extends Selector { public V0() { super(0); } }
    public static final class V1 extends Selector { public V1() { super(1); } }
    public static final class V2 extends Selector { public V2() { super(2); } }
    public static final class V3 extends Selector { public V3() { super(3); } }
    public static final class V4 extends Selector { public V4() { super(4); } }
    public static final class V5 extends Selector { public V5() { super(5); } }
    public static final class V6 extends Selector { public V6() { super(6); } }
    public static final class V7 extends Selector { public V7() { super(7); } }
    public static final class V8 extends Selector { public V8() { super(8); } }
    public static final class V9 extends Selector { public V9() { super(9); } }
    public static final class V10 extends Selector { public V10() { super(10); } }
    public static final class V11 extends Selector { public V11() { super(11); } }
}
