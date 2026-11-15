package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.gui.widgets.TankWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.RecipeProgressWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.NotNull;

public class DistillationTowerUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /** The layout is drawn at fixed coordinates, so the panel is the size the layout was drawn for. */
    private static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 175;
    /** The one fluid input, off to the left of the tower. */
    private static final int INPUT_X = 40, INPUT_Y = 37;
    /** The item output - the tower's residue - which sits beside the bottom row of fluid outputs. */
    private static final int ITEM_OUTPUT_X = 94, ITEM_OUTPUT_Y = 55;
    /** Fluid outputs fill three to a row, bottom row first, the way the tower's plates stack upwards. */
    private static final int FLUID_OUTPUT_X = 113, FLUID_OUTPUT_Y = 55, FLUIDS_PER_ROW = 3;
    /** One beaker per column, so a fluid keeps its beaker however many rows the outputs end up filling. */
    private static final IDrawable[] BEAKERS = { GTGuiTextures.BEAKER_OVERLAY_2, GTGuiTextures.BEAKER_OVERLAY_3,
            GTGuiTextures.BEAKER_OVERLAY_4 };

    public DistillationTowerUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, true, false, false);
    }

    @Override
    protected void addSlot(ModularUI.Builder builder, int x, int y, int slotIndex, IItemHandlerModifiable itemHandler,
                           FluidTankList fluidHandler, boolean isFluid, boolean isOutputs) {
        if (isFluid) {
            TankWidget tankWidget = new TankWidget(fluidHandler.getTankAt(slotIndex), x, y, 18, 18);
            TextureArea base = GuiTextures.FLUID_SLOT;

            if (!isOutputs)
                tankWidget.setBackgroundTexture(base, GuiTextures.BEAKER_OVERLAY_1);
            else if (slotIndex == 0 || slotIndex == 3 || slotIndex == 6 || slotIndex == 9)
                tankWidget.setBackgroundTexture(base, GuiTextures.BEAKER_OVERLAY_2);
            else if (slotIndex == 1 || slotIndex == 4 || slotIndex == 7 || slotIndex == 10)
                tankWidget.setBackgroundTexture(base, GuiTextures.BEAKER_OVERLAY_3);
            else if (slotIndex == 2 || slotIndex == 5 || slotIndex == 8 || slotIndex == 11)
                tankWidget.setBackgroundTexture(base, GuiTextures.BEAKER_OVERLAY_4);

            tankWidget.setAlwaysShowFull(true);
            builder.widget(tankWidget);
        } else {
            SlotWidget slotWidget = new SlotWidget(itemHandler, slotIndex, x, y, true, !isOutputs);
            TextureArea base = GuiTextures.SLOT;

            slotWidget.setBackgroundTexture(base, GuiTextures.DUST_OVERLAY);

            builder.widget(slotWidget);
        }
    }

    /**
     * The tower boils one fluid apart into a column of up to twelve, which is nothing like the input block / output
     * block grid the default layout builds. This is the MUI1 {@link #createJeiUITemplate} layout, widget for widget.
     */
    @Override
    public ModularPanel constructRecipeViewerPanel(@NotNull String name, @NotNull RecipeViewerLayout layout) {
        PanelBuilder builder = new PanelBuilder().slotListener(layout.slotListener());
        ModularPanel panel = GTGuis.createPanel(name, PANEL_WIDTH, PANEL_HEIGHT);

        panel.child(new RecipeProgressWidget()
                .recipeMap(recipeMap())
                .pos(47, 8)
                .size(66, 58)
                .value(viewerProgress(layout.progress()))
                .texture(GTGuiTextures.PROGRESS_BAR_DISTILLATION_TOWER, 66)
                .direction(Direction.RIGHT));

        panel.child(builder.makeFluidSlot(0, layout.importFluids(), false)
                .overlay(GTGuiTextures.BEAKER_OVERLAY_1)
                .pos(INPUT_X, INPUT_Y));

        panel.child(builder.makeItemSlot(new SlotGroup("output_items", 1, 1, false), 0, layout.exportItems(), true)
                .overlay(GTGuiTextures.DUST_OVERLAY)
                .pos(ITEM_OUTPUT_X, ITEM_OUTPUT_Y));

        for (int i = 0; i < layout.exportFluids().getTanks(); i++) {
            panel.child(builder.makeFluidSlot(i, layout.exportFluids(), true)
                    .overlay(BEAKERS[i % FLUIDS_PER_ROW])
                    .pos(FLUID_OUTPUT_X + 18 * (i % FLUIDS_PER_ROW),
                            FLUID_OUTPUT_Y - 18 * (i / FLUIDS_PER_ROW)));
        }
        return panel;
    }

    @Override
    public ModularUI.Builder createJeiUITemplate(IItemHandlerModifiable importItems, IItemHandlerModifiable exportItems,
                                                 FluidTankList importFluids, FluidTankList exportFluids, int yOffset) {
        ModularUI.Builder builder = ModularUI.defaultBuilder(yOffset);
        builder.widget(new ProgressWidget(200, 47, 8, 66, 58, GuiTextures.PROGRESS_BAR_DISTILLATION_TOWER,
                ProgressWidget.MoveType.HORIZONTAL));
        addInventorySlotGroup(builder, importItems, importFluids, false, 9);
        addInventorySlotGroup(builder, exportItems, exportFluids, true, 9);
        if (specialTexture() != null && specialTexturePosition() != null) {
            addSpecialTexture(builder);
        }
        return builder;
    }

    @Override
    protected void addInventorySlotGroup(@NotNull ModularUI.Builder builder,
                                         @NotNull IItemHandlerModifiable itemHandler,
                                         @NotNull FluidTankList fluidHandler, boolean isOutputs, int yOffset) {
        int itemInputsCount = itemHandler.getSlots();
        int fluidInputsCount = fluidHandler.getTanks();
        boolean invertFluids = false;
        if (itemInputsCount == 0) {
            int tmp = itemInputsCount;
            itemInputsCount = fluidInputsCount;
            fluidInputsCount = tmp;
            invertFluids = true;
        }
        int[] inputSlotGrid = RecipeMapUI.determineSlotsGrid(itemInputsCount);
        int itemSlotsToLeft = inputSlotGrid[0];
        int itemSlotsToDown = inputSlotGrid[1];
        int startInputsX = isOutputs ? 104 : 68 - itemSlotsToLeft * 18;
        int startInputsY = 55 - (int) (itemSlotsToDown / 2.0 * 18) + yOffset;
        boolean wasGroupOutput = itemHandler.getSlots() + fluidHandler.getTanks() == 12;
        if (wasGroupOutput && isOutputs) startInputsY -= 9;
        if (itemHandler.getSlots() == 6 && fluidHandler.getTanks() == 2 && !isOutputs) startInputsY -= 9;
        if (!isOutputs) {
            addSlot(builder, 40, startInputsY + (itemSlotsToDown - 1) * 18 - 18, 0, itemHandler, fluidHandler,
                    invertFluids, false);
        } else {
            addSlot(builder, 94, startInputsY + (itemSlotsToDown - 1) * 18, 0, itemHandler, fluidHandler, invertFluids,
                    true);
        }

        if (wasGroupOutput) startInputsY += 2;

        if (!isOutputs) return;

        if (!invertFluids) {
            startInputsY -= 18;
            startInputsX += 9;
        }

        if (fluidInputsCount > 0 || invertFluids) {
            int startSpecY = startInputsY + itemSlotsToDown * 18;
            for (int i = 0; i < fluidInputsCount; i++) {
                int x = startInputsX + 18 * (i % 3);
                int y = startSpecY - (i / 3) * 18;
                addSlot(builder, x, y, i, itemHandler, fluidHandler, true, true);
            }
        }
    }
}
