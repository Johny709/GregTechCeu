package gregtech.mixins.jei;

import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.gui.ingredients.GuiIngredientGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


@Mixin(value = GuiIngredientGroup.class, remap = false)
public interface GuiIngredientGroupAccessor {

    @Accessor(value = "inputSlots")
    IntSet getInputSlotIndexes();
}
