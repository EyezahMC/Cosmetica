package com.eyezah.cosmetics.screens;

import benzenestudios.sulphate.Anchor;
import benzenestudios.sulphate.SulphateScreen;
import com.eyezah.cosmetics.Cosmetica;
import com.eyezah.cosmetics.utils.Debug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.io.IOException;

public class CosmeticaSettingsScreen extends SulphateScreen {
	public CosmeticaSettingsScreen(Screen parentScreen, ServerOptions oldOptions) {
		super(new TranslatableComponent("cosmetica.cosmeticaSettings"), parentScreen);
		this.parentScreen = parentScreen;

		this.oldOptions = oldOptions;
		this.newOptions = new ServerOptions(this.oldOptions);

		this.setAnchorY(Anchor.TOP, () -> this.height / 6);
		this.setRows(2);
	}

	private final Screen parentScreen;
	private final ServerOptions oldOptions;
	private final ServerOptions newOptions;

	private TextComponent generateButtonToggleText(String translatable, boolean toggle) {
		TextComponent component = new TextComponent("");
		component.append(new TranslatableComponent(translatable));
		component.append(": ");
		if (toggle) {
			component.append(new TranslatableComponent("cosmetica.enabled"));
		} else {
			component.append(new TranslatableComponent("cosmetica.disabled"));
		}
		return component;
	}

	@Override
	protected void addWidgets() {
		// top row
		this.addButton(generateButtonToggleText("cosmetica.australians", this.newOptions.regionSpecificEffects.get()), button -> {
			this.newOptions.regionSpecificEffects.toggle();
			button.setMessage(generateButtonToggleText("cosmetica.australians", this.newOptions.regionSpecificEffects.get()));
		});

		this.addButton(generateButtonToggleText("cosmetica.doHats", this.newOptions.hats.get()), button -> {
			this.newOptions.hats.toggle();
			button.setMessage(generateButtonToggleText("cosmetica.doHats", this.newOptions.hats.get()));
		});

		// second row, etc...

		this.addButton(generateButtonToggleText("cosmetica.doLore", this.newOptions.lore.get()), (button) -> {
			this.newOptions.lore.toggle();
			button.setMessage(generateButtonToggleText("cosmetica.doLore", this.newOptions.lore.get()));
		});

		this.addButton(generateButtonToggleText("cosmetica.doShoulderBuddies", this.newOptions.shoulderBuddies.get()), (button) -> {
			this.newOptions.shoulderBuddies.toggle();
			button.setMessage(generateButtonToggleText("cosmetica.doShoulderBuddies", this.newOptions.shoulderBuddies.get()));
		});

		this.addButton(generateButtonToggleText("cosmetica.showNametagInThirdPerson", Cosmetica.getConfig().shouldShowNametagInThirdPerson()), (button) -> {
			Cosmetica.getConfig().setShowNametagInThirdPerson(!Cosmetica.getConfig().shouldShowNametagInThirdPerson());
			button.setMessage(generateButtonToggleText("cosmetica.showNametagInThirdPerson", Cosmetica.getConfig().shouldShowNametagInThirdPerson()));
		});

		this.addButton(generateButtonToggleText("cosmetica.doBackBlings", this.newOptions.backBlings.get()), (button) -> {
			this.newOptions.backBlings.toggle();
			button.setMessage(generateButtonToggleText("cosmetica.doBackBlings", this.newOptions.backBlings.get()));
		});

		// when done, update settings
		this.addRenderableWidget(new Button(this.width / 2 - 100, this.height / 6 + 24 * 3, 200, 20, CommonComponents.GUI_DONE, (button) -> {
			try {
				if (this.parentScreen instanceof MainScreen main) {
					main.setCosmeticaOptions(this.newOptions);
				}

				this.minecraft.setScreen(new UpdatingSettingsScreen(this.parentScreen, this.oldOptions, this.newOptions));
			} catch (IOException e) {
				e.printStackTrace();
				this.minecraft.setScreen(this.parentScreen);
			} catch (InterruptedException e) {
				e.printStackTrace();
				this.minecraft.setScreen(this.parentScreen);
			}

			try {
				Cosmetica.getConfig().save();
			} catch (IOException e) {
				Cosmetica.LOGGER.warn("Failed to save cosmetica config!");
				e.printStackTrace();
			}
		}));
	}
}
