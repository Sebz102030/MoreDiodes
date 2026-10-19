package com.power.morediodes.client;

import com.power.morediodes.assembly.FanStepRecipe;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** JEI column for a fan step: an Encased Fan with the catalyst (lava / fire / soul fire) under it. */
public class FanStepSubCategory extends SequencedAssemblySubCategory {

    public FanStepSubCategory() {
        super(25);
    }

    @Override
    public void draw(SequencedRecipe<?> recipe, GuiGraphics graphics, double mouseX, double mouseY, int index) {
        graphics.renderItem(new ItemStack(AllBlocks.ENCASED_FAN.get()), 4, 46);
        if (recipe.getRecipe() instanceof FanStepRecipe step)
            graphics.renderItem(new ItemStack(step.catalystIcon()), 4, 66);
    }
}
