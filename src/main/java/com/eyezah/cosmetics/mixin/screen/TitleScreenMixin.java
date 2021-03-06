package com.eyezah.cosmetics.mixin.screen;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.eyezah.cosmetics.Authentication.runAuthentication;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
	@Inject(at = @At("HEAD"), method = "init")
	private void titleScreenInject(CallbackInfo ci) {
		runAuthentication(2);
	}
}