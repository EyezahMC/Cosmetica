package com.eyezah.cosmetics.cosmetics.model;

import cc.cosmetica.api.Box;
import cc.cosmetica.api.Model;
import com.eyezah.cosmetics.Cosmetica;
import com.eyezah.cosmetics.screens.fakeplayer.FakePlayerRenderer;
import com.eyezah.cosmetics.utils.Debug;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

public class Models {
	private static Map<String, BakedModel> BAKED_MODELS = new HashMap<>();
	private static Map<String, BakableModel> LOADED_MODELS = new HashMap<>();
	private static final float RANDOM_NEXT_FLOAT = 0.211f; // generated by random.org. Guaranteed to be random.
	public static ModelBakery thePieShopDownTheRoad;
	public static final RuntimeTextureManager TEXTURE_MANAGER = new RuntimeTextureManager(128); // reserved 0 through 127

	/**
	 * PLEASE DO NOT CALL THIS DIRECTLY (call Cosmetics#clearAllCaches(), or resetTextureBasedCaches() to clear texture related caches)
	 */
	public static void resetCaches() {
		LOADED_MODELS = new HashMap<>();
		resetTextureBasedCaches();
	}

	public static void resetTextureBasedCaches() {
		BAKED_MODELS = new HashMap<>();
		TEXTURE_MANAGER.clear();
	}

	public static Collection<String> getCachedModels() {
		return LOADED_MODELS.keySet();
	}

	@Nullable
	public static BakedModel getBakedModel(BakableModel unbaked) {
		if (unbaked.id().charAt(0) == '-') return null; // help i wrote this at 1:!5am
		boolean compute = !BAKED_MODELS.containsKey(unbaked.id());

		if (compute) {
			Debug.info("Computing Baked Model: " + unbaked.id());
			BAKED_MODELS.put(unbaked.id(), null); // searching
			TEXTURE_MANAGER.retrieveAllocatedSprite(unbaked, Minecraft.getInstance().level == null ? FakePlayerRenderer.tickTime : Minecraft.getInstance().level.getGameTime(), sprite -> {
				BakedModel model = unbaked.model().bake(
						thePieShopDownTheRoad,
						l -> Models.getAppropriateTexture(l, sprite, unbaked.id()),
						BlockModelRotation.X0_Y0,
						new ResourceLocation(unbaked.id().toLowerCase(Locale.ROOT)) /*this resource location is just for debugging in the case of errors*/);
				BAKED_MODELS.put(unbaked.id(), model);
			});
		}

		return BAKED_MODELS.get(unbaked.id());
	}

	/**
	 * @apiNote result is nullable if and only if "allocated" is null.
	 */
	private static TextureAtlasSprite getAppropriateTexture(Material material, TextureAtlasSprite allocated, String id) {
		if (material.texture().getNamespace().equals("minecraft")) { // if in the "minecraft" namespace they may be requesting a vanilla texture.
			TextureAtlasSprite vanillaSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(material.texture());

			if (vanillaSprite == null) {
				Cosmetica.LOGGER.warn("Model " + id + " requested a 'minecraft' texture but the associated sprite is NULL! Requested texture: " + material.texture() + ", Fallback Sprite: " + allocated);
			}
		}

		return allocated;
	}

	public static void removeBakedModel(String id) {
		BAKED_MODELS.remove(id);
	}

	/**
	 * @returns the existing or created bakable model. Will be null if there is an error creating the model
	 * @implNote used when adding the BakableModel to player data
	 */
	@Nullable
	public static BakableModel createBakableModel(Model model) {
		String location = model.getId();

		if (location.isEmpty()) return null;

		Box bounds = model.getBoundingBox();

		if (model.isBuiltin()) {
			return LOADED_MODELS.computeIfAbsent(location, l -> new BakableModel(location, model.getName(), null, null, 0, bounds));
		}

		return LOADED_MODELS.computeIfAbsent(location, l -> {
			try (InputStream is = new ByteArrayInputStream(model.getModel().getBytes(StandardCharsets.UTF_8))) {
				BlockModel blockModel = BlockModel.fromStream(new InputStreamReader(is, StandardCharsets.UTF_8));
				blockModel.name = l;
				NativeImage image = NativeImage.fromBase64(model.getTexture().substring(22)); // trim nonsense at the start
				return new BakableModel(location, model.getName(), blockModel, image, model.flags(), bounds);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});
	}

	public static void renderModel(BakedModel model, PoseStack stack, MultiBufferSource multiBufferSource, int packedLight) {
		stack.pushPose();
		boolean isGUI3D = model.isGui3d();
		float transformStrength = 0.25F;
		float rotation = 0.0f;
		float transform = model.getTransforms().getTransform(ItemTransforms.TransformType.GROUND).scale.y();
		stack.translate(0.0D, rotation + transformStrength * transform, 0.0D);
		float xScale = model.getTransforms().ground.scale.x();
		float yScale = model.getTransforms().ground.scale.y();
		float zScale = model.getTransforms().ground.scale.z();

		stack.pushPose();

		final ItemTransforms.TransformType transformType = ItemTransforms.TransformType.FIXED;
		int overlayTyp = OverlayTexture.NO_OVERLAY;
		// ItemRenderer#render start
		stack.pushPose();

		model.getTransforms().getTransform(transformType).apply(false, stack);
		stack.translate(-0.5D, -0.5D, -0.5D);

		RenderType renderType = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS); // hopefully this is the right one
		VertexConsumer vertexConsumer4 = multiBufferSource.getBuffer(renderType);
		renderModelLists(model, packedLight, overlayTyp, stack, vertexConsumer4);

		stack.popPose();
		// ItemRenderer#render end

		stack.popPose();
		if (!isGUI3D) {
			stack.translate(0.0F * xScale, 0.0F * yScale, 0.09375F * zScale);
		}

		stack.popPose();
	}

	// vanilla code that I don't want to rewrite:

	private static void renderModelLists(BakedModel bakedModel, int packedLight, int overlayType, PoseStack poseStack, VertexConsumer vertexConsumer) {
		Random random = new Random();
		final long seed = 42L;
		Direction[] var10 = Direction.values();
		int var11 = var10.length;

		for(int var12 = 0; var12 < var11; ++var12) {
			Direction direction = var10[var12];
			random.setSeed(seed);
			renderQuadList(poseStack, vertexConsumer, bakedModel.getQuads(null, direction, random), packedLight, overlayType);
		}

		random.setSeed(seed);
		renderQuadList(poseStack, vertexConsumer, bakedModel.getQuads(null, null, random), packedLight, overlayType);
	}

	private static void renderQuadList(PoseStack poseStack, VertexConsumer vertexConsumer, List<BakedQuad> list, int i, int j) {
		PoseStack.Pose pose = poseStack.last();
		Iterator var9 = list.iterator();

		while(var9.hasNext()) {
			BakedQuad bakedQuad = (BakedQuad)var9.next();
			int k = -1;

			float f = (float)(k >> 16 & 255) / 255.0F;
			float g = (float)(k >> 8 & 255) / 255.0F;
			float h = (float)(k & 255) / 255.0F;
			vertexConsumer.putBulkData(pose, bakedQuad, f, g, h, i, j);
		}
	}
}
