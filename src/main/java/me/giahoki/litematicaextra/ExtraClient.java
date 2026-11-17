package me.giahoki.litematicaextra;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public final class ExtraClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        PlacementSettings.init();
        AttackBlockCallback.EVENT.register((player,world,hand,pos,direction)->
                NativeCreativeWorld.isStarter(world,pos)?ActionResult.FAIL:ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->!NativeCreativeWorld.isStarter(world,pos));
        ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof TitleScreen){
                var buttons=Screens.getButtons(screen);
                buttons.stream().filter(button->button.getMessage().equals(Text.translatable("menu.options")))
                        .findFirst().ifPresent(options->{
                            Text label=Text.literal("Litematica extra");
                            var shortcut=TextIconButtonWidget.builder(label,
                                            button->client.setScreen(new SchematicWorldSelectScreen(screen)),true)
                                    .dimension(20,20)
                                    .texture(Identifier.of("litematica_extra","title_shortcut"),14,14)
                                    .build();
                            int x=options.getX()-48;
                            shortcut.setPosition(Math.max(2,x),options.getY()-(x<2?24:0));
                            shortcut.setTooltip(Tooltip.of(label));
                            buttons.add(shortcut);
                        });
            }
            if(screen instanceof GameMenuScreen&&NativeCreativeWorld.isEditing()){
                var buttons=Screens.getButtons(screen);
                if(buttons.isEmpty())return;
                Text physicsLabel=ExtraText.label("world.physics");
                int physicsWidth=Math.max(64,client.textRenderer.getWidth(physicsLabel)+12);
                int saveWidth=Math.min(149,(w-28-physicsWidth)/2);
                buttons.add(ButtonWidget.builder(ExtraText.label("save.file"),b->NativeCreativeWorld.promptSave(screen,false)).dimensions(8,8,saveWidth,20).build());
                buttons.add(ButtonWidget.builder(ExtraText.label("save.exit"),b->NativeCreativeWorld.promptSave(screen,true)).dimensions(14+saveWidth,8,saveWidth,20).build());
                buttons.add(ButtonWidget.builder(physicsLabel,b->{
                    CreativePhysics physics=NativeCreativeWorld.getPhysics();
                    if(physics!=null)client.setScreen(new CreativePhysicsScreen(screen,physics));
                }).dimensions(20+2*saveWidth,8,physicsWidth,20).build());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(NativeCreativeWorld::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(NativeCreativeWorld::serverTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(NativeCreativeWorld::serverStopping);
    }
}
