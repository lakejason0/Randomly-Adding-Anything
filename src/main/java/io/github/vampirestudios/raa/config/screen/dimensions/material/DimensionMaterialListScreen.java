package io.github.vampirestudios.raa.config.screen.dimensions.material;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.vampirestudios.raa.config.screen.ConfigScreen;
import io.github.vampirestudios.raa.generation.materials.DimensionMaterial;
import io.github.vampirestudios.raa.generation.materials.Material;
import io.github.vampirestudios.raa.registries.Materials;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.TranslatableText;
import org.apache.commons.lang3.text.WordUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class DimensionMaterialListScreen extends Screen {

    Screen parent;
    String tooltip = null;
    private RAADimensionMaterialListWidget materialList;
    private RAADimensionMaterialDescriptionListWidget descriptionList;

    public DimensionMaterialListScreen(Screen parent) {
        super(new TranslatableText("config.title.raa.dimensionMaterial"));
        this.parent = parent;
    }

    @Override
    public boolean keyPressed(int int_1, int int_2, int int_3) {
        if (int_1 == 256) {
            minecraft.openScreen(parent);
            return true;
        }
        return super.keyPressed(int_1, int_2, int_3);
    }

    @Override
    protected void init() {
        super.init();
        addButton(new ButtonWidget(4, 4, 50, 20, I18n.translate("gui.back"), var1 -> minecraft.openScreen(parent)));
        children.add(materialList = new RAADimensionMaterialListWidget(minecraft, width / 2 - 10, height,
                28 + 5, height - 5, BACKGROUND_LOCATION));
        children.add(descriptionList = new RAADimensionMaterialDescriptionListWidget(minecraft, width / 2 - 10, height,
                28 + 5, height - 5, BACKGROUND_LOCATION));
        materialList.setLeftPos(5);
        descriptionList.setLeftPos(width / 2 + 5);
        List<DimensionMaterial> dimensionMaterials = new ArrayList<>();
        for (DimensionMaterial material : Materials.DIMENSION_MATERIALS) dimensionMaterials.add(material);
        dimensionMaterials.sort(Comparator.comparing(material -> WordUtils.capitalizeFully(material.getName()), String::compareToIgnoreCase));
        for (DimensionMaterial material : dimensionMaterials) {
            materialList.addItem(new RAADimensionMaterialListWidget.MaterialEntry(material) {
                @Override
                public void onClick() {
                    descriptionList.addMaterial(DimensionMaterialListScreen.this, material);
                }

                @Override
                public boolean isSelected(Material material) {
                    return descriptionList.material == material;
                }
            });
        }
        if (!dimensionMaterials.isEmpty()) materialList.addItem(new RAADimensionMaterialListWidget.EmptyEntry(10));
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        tooltip = null;
        renderDirtBackground(0);
        materialList.render(mouseX, mouseY, delta);
        descriptionList.render(mouseX, mouseY, delta);
        ConfigScreen.overlayBackground(0, 0, width, 28, 64, 64, 64, 255, 255);
        ConfigScreen.overlayBackground(0, height - 5, width, height, 64, 64, 64, 255, 255);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA.value, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA.value,
                GlStateManager.SrcFactor.ZERO.value, GlStateManager.DstFactor.ONE.value
        );
        RenderSystem.disableAlphaTest();
        RenderSystem.shadeModel(7425);
        RenderSystem.disableTexture();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(0, 28 + 4, 0.0D).texture(0.0F, 1.0F).color(0, 0, 0, 0).next();
        buffer.vertex(this.width, 28 + 4, 0.0D).texture(1.0F, 1.0F).color(0, 0, 0, 0).next();
        buffer.vertex(this.width, 28, 0.0D).texture(1.0F, 0.0F).color(0, 0, 0, 255).next();
        buffer.vertex(0, 28, 0.0D).texture(0.0F, 0.0F).color(0, 0, 0, 255).next();
        tessellator.draw();
        RenderSystem.enableTexture();
        RenderSystem.shadeModel(7424);
        RenderSystem.enableAlphaTest();
        RenderSystem.disableBlend();
        drawCenteredString(font, title.asFormattedString(), width / 2, 10, 16777215);
        super.render(mouseX, mouseY, delta);
        if (tooltip != null)
            renderTooltip(Arrays.asList(tooltip.split("\n")), mouseX, mouseY);
    }

}
