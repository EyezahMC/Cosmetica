package com.eyezah.cosmetics.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.eyezah.cosmetics.Cosmetics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
	@Shadow
	@Final
	private EntityRenderDispatcher entityRenderDispatcher;

	@Shadow
	@Final
	private Font font;

	@Inject(at = @At("RETURN"), method = "renderNameTag")
	protected void onRenderNameTag(Entity entity, Component component, PoseStack matrixStack, MultiBufferSource multiBufferSource, int i, CallbackInfo info) {
		Cosmetics.onRenderNameTag(this.entityRenderDispatcher, entity, matrixStack, multiBufferSource, this.font, i);
	}
}
