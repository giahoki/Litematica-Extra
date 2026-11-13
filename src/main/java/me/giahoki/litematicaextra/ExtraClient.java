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
                int base=h/4+48;
                for(var button:Screens.getButtons(screen))if(button.getWidth()==200&&button.getX()==w/2-100&&button.getY()>=base&&button.getY()<=base+48)button.setY(button.getY()-12);
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Litematica extra"),b->client.setScreen(new SchematicWorldSelectScreen(screen))).dimensions(w/2-100,base+60,200,20).build());
            }
            if(screen instanceof GameMenuScreen&&NativeCreativeWorld.isEditing()){
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Сохранить .litematic"),b->NativeCreativeWorld.promptSave(screen,false)).dimensions(8,8,149,20).build());
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Сохранить и выйти"),b->NativeCreativeWorld.promptSave(screen,true)).dimensions(163,8,149,20).build());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(NativeCreativeWorld::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(NativeCreativeWorld::serverTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(NativeCreativeWorld::serverStopping);
    }
}
