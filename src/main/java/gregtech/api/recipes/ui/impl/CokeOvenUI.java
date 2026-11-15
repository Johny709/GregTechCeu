package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.RecipeProgressWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class CokeOvenUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /** The oven's layout is squat compared to a machine gui, so the panel is the size the layout was drawn for. */
    private static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 100;

    /**
     * @param recipeMap the recipemap corresponding to this ui
     */
    public CokeOvenUI(@NotNull R recipeMap) {
        super(recipeMap, false, false, false, false, false);
    }

    /**
     * The oven takes one item and gives back an item and a fluid, stacked one above the other rather than side by
     * side in a grid. This is the MUI1 {@link #createJeiUITemplate} layout, widget for widget.
     */
    @Override
    public ModularPanel constructRecipeViewerPanel(@NotNull String name, @NotNull RecipeViewerLayout layout) {
        PanelBuilder builder = new PanelBuilder().slotListener(layout.slotListener());
        ModularPanel panel = GTGuis.createPanel(name, PANEL_WIDTH, PANEL_HEIGHT);
        SlotGroup outputs = new SlotGroup("output_items", 1, 1, false);

        panel.child(new RecipeProgressWidget()
                .recipeMap(recipeMap())
                .pos(70, 19)
                .size(36, 18)
                .value(viewerProgress(layout.progress()))
                .texture(GTGuiTextures.PROGRESS_BAR_COKE_OVEN, 36)
                .direction(Direction.RIGHT));

        panel.child(builder.makeItemSlot(new SlotGroup("input_items", 1, 1, true), 0, layout.importItems(), false)
                .pos(52, 10));
        panel.child(builder.makeItemSlot(outputs, 0, layout.exportItems(), true)
                .pos(106, 10));
        panel.child(builder.makeFluidSlot(0, layout.exportFluids(), true)
                .pos(106, 28));
        return panel;
    }

    @Override
    public ModularUI.Builder createJeiUITemplate(IItemHandlerModifiable importItems, IItemHandlerModifiable exportItems,
                                                 FluidTankList importFluids, FluidTankList exportFluids, int yOffset) {
        ModularUI.Builder builder = ModularUI.builder(GuiTextures.BACKGROUND, 176, 100)
                .widget(new ProgressWidget(200, 70, 19, 36, 18, GuiTextures.PROGRESS_BAR_COKE_OVEN,
                        ProgressWidget.MoveType.HORIZONTAL));
        addSlot(builder, 52, 10, 0, importItems, null, false, false);
        addSlot(builder, 106, 10, 0, exportItems, null, false, true);
        addSlot(builder, 106, 28, 0, null, exportFluids, true, true);
        return builder;
    }
}
