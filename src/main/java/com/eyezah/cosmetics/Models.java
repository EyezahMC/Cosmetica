package com.eyezah.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.stack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

import static net.minecraft.client.renderer.entity.ItemRenderer.getFoilBufferDirect;

public class Models {
	private static final float RANDOM_NEXT_FLOAT = 0.211f; // generated by random.org. Guaranteed to be random.

	public static void renderModel(BakedModel model, PoseStack stack, Level level, MultiBufferSource multiBufferSource) {
		stack.pushPose();
		boolean isGUI3D = model.isGui3d();
		int renderAmount = 1;
		float h = 0.25F;
		float rotation = 0.0f;//Mth.sin(((float)itemEntity.getAge() + g) / 10.0F + itemEntity.bobOffs) * 0.1F + 0.1F;
		float m = model.getTransforms().getTransform(ItemTransforms.TransformType.GROUND).scale.y();
		stack.translate(0.0D, (double)(rotation + 0.25F * m), 0.0D);
		float n = 0;//itemEntity.getSpin(g);
		stack.mulPose(Vector3f.YP.rotation(n));
		float o = model.getTransforms().ground.scale.x();
		float p = model.getTransforms().ground.scale.y();
		float q = model.getTransforms().ground.scale.z();
		float yShift;
		float zShift;
		if (!isGUI3D) {
			float xShift = -0.0F * (float)(renderAmount - 1) * 0.5F * o;
			yShift = -0.0F * (float)(renderAmount - 1) * 0.5F * p;
			zShift = -0.09375F * (float)(renderAmount - 1) * 0.5F * q;
			stack.translate(xShift, yShift, zShift);
		}

		for(int u = 0; u < renderAmount; ++u) {
			stack.pushPose();
			if (u > 0) {
				if (isGUI3D) {
					yShift = (RANDOM_NEXT_FLOAT * 2.0F - 1.0F) * 0.15F;
					zShift = (RANDOM_NEXT_FLOAT * 2.0F - 1.0F) * 0.15F;
					float x = (RANDOM_NEXT_FLOAT * 2.0F - 1.0F) * 0.15F;
					stack.translate(yShift, zShift, x);
				} else {
					yShift = (RANDOM_NEXT_FLOAT * 2.0F - 1.0F) * 0.15F * 0.5F;
					zShift = (RANDOM_NEXT_FLOAT * 2.0F - 1.0F) * 0.15F * 0.5F;
					stack.translate(yShift, zShift, 0.0D);
				}
			}

			final ItemTransforms.TransformType transformType = ItemTransforms.TransformType.FIXED;
			int j = OverlayTexture.NO_OVERLAY;
			stack.pushPose();

			model.getTransforms().getTransform(transformType).apply(false, stack);
				stack.translate(-0.5D, -0.5D, -0.5D);

				boolean bl4 = true;
				RenderType renderType = RenderType.cutoutMipped(); // hopefully this is the right one
				VertexConsumer vertexConsumer4 = multiBufferSource.getBuffer(renderType);
				renderModelLists(bakedModel, itemStack, i, j, stack, vertexConsumer4);

				stack.popPose();
			
			stack.popPose();
			if (!isGUI3D) {
				stack.translate(0.0F * o, 0.0F * p, 0.09375F * q);
			}
		}

		stack.popPose();
	}

	private void renderModelLists(BakedModel bakedModel, ItemStack itemStack, int i, int j, PoseStack poseStack, VertexConsumer vertexConsumer) {
		Random random = new Random();
		long l = 42L;
		Direction[] var10 = Direction.values();
		int var11 = var10.length;

		for(int var12 = 0; var12 < var11; ++var12) {
			Direction direction = var10[var12];
			random.setSeed(42L);
			this.renderQuadList(poseStack, vertexConsumer, bakedModel.getQuads((BlockState)null, direction, random), itemStack, i, j);
		}

		random.setSeed(42L);
		this.renderQuadList(poseStack, vertexConsumer, bakedModel.getQuads((BlockState)null, (Direction)null, random), itemStack, i, j);
	}
}
