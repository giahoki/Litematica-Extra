package me.giahoki.litematicaextra.mixin;

import java.util.List;
import me.giahoki.litematicaextra.NativeCreativeWorld;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldListWidget.class)
public abstract class TemporaryWorldListMixin {
    @ModifyVariable(method="showSummaries",at=@At("HEAD"),argsOnly=true)
    private List<LevelSummary> extra$hideTemporaryWorlds(List<LevelSummary> worlds,String search){
        if(search.startsWith("litematica-extra-"))return worlds;
        return worlds.stream().filter(world->!NativeCreativeWorld.isTemporaryWorld(world.getName())).toList();
    }
}
