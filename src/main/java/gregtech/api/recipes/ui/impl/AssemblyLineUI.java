package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.gui.widgets.SlotWidget;
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
public final class AssemblyLineUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /** The layout is drawn at fixed coordinates, so the panel is the size the layout was drawn for. */
    private static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 176;
    /** Top left of the input grid, the anchor every other position in the layout is measured from. */
    private static final int GRID_X = 80 - 4 * 18, GRID_Y = 37 - 2 * 18;
    /** The item input grid is square, and the data slot is the one input slot outside it. */
    private static final int GRID_SIZE = 4, DATA_SLOT = 16;

    /**
     * @param recipeMap the recipemap corresponding to this ui
     */
    public AssemblyLineUI(@NotNull R recipeMap) {
        super(recipeMap, false, false, false, false, false);
        setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressWidget.MoveType.HORIZONTAL);
    }

    @Override
    @NotNull
    public ModularUI.Builder createJeiUITemplate(IItemHandlerModifiable importItems, IItemHandlerModifiable exportItems,
                                                 FluidTankList importFluids, FluidTankList exportFluids, int yOffset) {
        ModularUI.Builder builder = ModularUI.builder(GuiTextures.BACKGROUND, 176, 176)
                .widget(new ProgressWidget(200, 80, 1, 54, 72, GuiTextures.PROGRESS_BAR_ASSEMBLY_LINE,
                        ProgressWidget.MoveType.HORIZONTAL))
                .widget(new ProgressWidget(200, 138, 19, 10, 18, GuiTextures.PROGRESS_BAR_ASSEMBLY_LINE_ARROW,
                        ProgressWidget.MoveType.VERTICAL));
        this.addInventorySlotGroup(builder, importItems, importFluids, false, yOffset);
        this.addInventorySlotGroup(builder, exportItems, exportFluids, true, yOffset);
        return builder;
    }

    /**
     * The assembly line's slots do not sit on the grid the default layout builds: the research data slot stands
     * apart from the 4x4 of item inputs, the fluids run down a column of their own, and two progress bars join them
     * to the single output. This is the MUI1 {@link #createJeiUITemplate} layout, widget for widget.
     */
    @Override
    public ModularPanel constructRecipeViewerPanel(@NotNull String name, @NotNull RecipeViewerLayout layout) {
        PanelBuilder builder = new PanelBuilder().slotListener(layout.slotListener());
        ModularPanel panel = GTGuis.createPanel(name, PANEL_WIDTH, PANEL_HEIGHT);
        SlotGroup inputs = new SlotGroup("input_items", GRID_SIZE, 1, true);
        SlotGroup outputs = new SlotGroup("output_items", 1, 1, false);

        panel.child(new RecipeProgressWidget()
                .recipeMap(recipeMap())
                .pos(80, 1)
                .size(54, 72)
                .value(viewerProgress(layout.progress()))
                .texture(GTGuiTextures.PROGRESS_BAR_ASSEMBLY_LINE, 54)
                .direction(Direction.RIGHT));
        panel.child(new RecipeProgressWidget()
                .recipeMap(recipeMap())
                .pos(138, 19)
                .size(10, 18)
                .value(viewerProgress(layout.progress()))
                .texture(GTGuiTextures.PROGRESS_BAR_ASSEMBLY_LINE_ARROW, 18)
                .direction(Direction.UP));

        // the research data stick, which the recipe viewer fills from the recipe's research property
        panel.child(builder.makeItemSlot(inputs, DATA_SLOT, layout.importItems(), false)
                .overlay(GTGuiTextures.DATA_ORB_OVERLAY)
                .pos(GRID_X + 18 * 7, GRID_Y + 18 * 2));

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int column = 0; column < GRID_SIZE; column++) {
                panel.child(builder
                        .makeItemSlot(inputs, row * GRID_SIZE + column, layout.importItems(), false)
                        .pos(GRID_X + 18 * column, GRID_Y + 18 * row));
            }
        }

        for (int i = 0; i < layout.importFluids().getTanks(); i++) {
            panel.child(builder.makeFluidSlot(i, layout.importFluids(), false)
                    .pos(GRID_X + 18 * 5, GRID_Y + 18 * i));
        }

        panel.child(builder.makeItemSlot(outputs, 0, layout.exportItems(), true)
                .pos(GRID_X + 18 * 7, GRID_Y));
        return panel;
    }

    @Override
    protected void addInventorySlotGroup(@NotNull ModularUI.Builder builder,
                                         @NotNull IItemHandlerModifiable itemHandler,
                                         @NotNull FluidTankList fluidHandler, boolean isOutputs, int yOffset) {
        int startInputsX = 80 - 4 * 18;
        int startInputsY = 37 - 2 * 18;

        if (!isOutputs) {
            // Data Slot
            builder.widget(new SlotWidget(itemHandler, 16, startInputsX + 18 * 7, 1 + 18 * 2, true, true)
                    .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.DATA_ORB_OVERLAY));

            // item input slots
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    int slotIndex = i * 4 + j;
                    addSlot(builder, startInputsX + 18 * j, startInputsY + 18 * i, slotIndex, itemHandler, fluidHandler,
                            false, false);
                }
            }

            // fluid slots
            int startFluidX = startInputsX + 18 * 5;
            for (int i = 0; i < 4; i++) {
                addSlot(builder, startFluidX, startInputsY + 18 * i, i, itemHandler, fluidHandler, true, false);
            }
        } else {
            // output slot
            addSlot(builder, startInputsX + 18 * 7, 1, 0, itemHandler, fluidHandler, false, true);
        }
    }
}
