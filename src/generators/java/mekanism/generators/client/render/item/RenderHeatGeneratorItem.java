package mekanism.generators.client.render.item;

import javax.annotation.Nonnull;
import com.mojang.blaze3d.matrix.MatrixStack;
import mekanism.api.NBTConstants;
import mekanism.client.render.item.ItemLayerWrapper;
import mekanism.client.render.item.MekanismItemStackRenderer;
import mekanism.common.util.ItemDataUtils;
import mekanism.generators.client.model.ModelHeatGenerator;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.ItemStack;

public class RenderHeatGeneratorItem extends MekanismItemStackRenderer {

    private static ModelHeatGenerator heatGenerator = new ModelHeatGenerator();
    public static ItemLayerWrapper model;

    @Override
    public void renderBlockSpecific(@Nonnull ItemStack stack, @Nonnull MatrixStack matrix, @Nonnull IRenderTypeBuffer renderer, int light, int overlayLight,
          TransformType transformType) {
        matrix.rotate(Vector3f.ZP.rotationDegrees(180));
        matrix.rotate(Vector3f.YP.rotationDegrees(180));
        matrix.translate(0, -1, 0);
        heatGenerator.render(matrix, renderer, light, overlayLight, ItemDataUtils.getDouble(stack, NBTConstants.ENERGY_STORED) > 0);
    }

    @Override
    protected void renderItemSpecific(@Nonnull ItemStack stack, @Nonnull MatrixStack matrix, @Nonnull IRenderTypeBuffer renderer, int light, int overlayLight,
          TransformType transformType) {
    }

    @Nonnull
    @Override
    protected TransformType getTransform(@Nonnull ItemStack stack) {
        return model.getTransform();
    }
}