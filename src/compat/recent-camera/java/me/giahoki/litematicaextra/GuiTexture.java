package me.giahoki.litematicaextra;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
final class GuiTexture { static TextureSetup setup(AbstractTexture texture){return TextureSetup.of(texture.getGlTextureView());} }
