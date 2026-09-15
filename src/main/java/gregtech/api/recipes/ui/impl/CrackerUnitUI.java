package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.gui.widgets.RecipeProgressWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.util.GTUtility;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.apache.commons.lang3.tuple.Pair;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

@ApiStatus.Internal
public class CrackerUnitUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /** The layout is drawn at fixed coordinates, so the panel is the size the layout was drawn for. */
    private static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    /**
     * Where the input bar hands over to the main one. The unit pulls the hydrogen or steam in first and cracks the
     * fuel with it after, so the two bars split the recipe evenly.
     */
    private static final double SPLIT = 0.5;

    public CrackerUnitUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, false, true, false);
        setFluidSlotOverlay(GuiTextures.CRACKING_OVERLAY_1, false);
        setFluidSlotOverlay(GuiTextures.CRACKING_OVERLAY_2, true);
        setItemSlotOverlay(GuiTextures.CIRCUIT_OVERLAY, false);
        setProgressBar(GuiTextures.PROGRESS_BAR_CRACKING, ProgressWidget.MoveType.HORIZONTAL);
    }

    /**
     * The unit's fluid inputs sit under the item input and feed a bar of their own, which runs up into the main one -
     * not the input block / output block grid the default layout builds. This is the MUI1
     * {@link #createJeiUITemplate} layout, widget for widget.
     */
    @Override
    public ModularPanel constructRecipeViewerPanel(@NotNull String name, @NotNull RecipeViewerLayout layout) {
        PanelBuilder builder = new PanelBuilder().slotListener(layout.slotListener());
        ModularPanel panel = GTGuis.createPanel(name, PANEL_WIDTH, PANEL_HEIGHT);
        SlotGroup inputs = new SlotGroup("input_items", 1, 1, true);

        // the fluids are drawn in first, then cracked, so the input bar leads and the main bar follows
        panel.child(new gregtech.api.mui.widget.RecipeProgressWidget()
                .recipeMap(recipeMap())
                .pos(42, 42)
                .size(21, 19)
                .value(new DoubleSyncValue(progressUpTo(layout.progress(), SPLIT)))
                .texture(GTGuiTextures.PROGRESS_BAR_CRACKING_INPUT, -1)
                .direction(Direction.UP));
        panel.child(new gregtech.api.mui.widget.RecipeProgressWidget()
                .recipeMap(recipeMap())
                .pos(78, 23)
                .size(20, 20)
                .value(new DoubleSyncValue(progressAfter(layout.progress(), SPLIT)))
                .texture(GTGuiTextures.PROGRESS_BAR_CRACKING, -1)
                .direction(Direction.RIGHT));

        // the circuit which picks the cracking type
        panel.child(builder.makeItemSlot(inputs, 0, layout.importItems(), false)
                .overlay(GTGuiTextures.CIRCUIT_OVERLAY)
                .pos(52, 24));

        // the fuel and what cracks it, filling leftwards under the circuit
        for (int i = 0; i < layout.importFluids().getTanks(); i++) {
            panel.child(builder.makeFluidSlot(i, layout.importFluids(), false)
                    .overlay(GTGuiTextures.CRACKING_OVERLAY_1)
                    .pos(52 - 18 * i, 61));
        }

        for (int i = 0; i < layout.exportFluids().getTanks(); i++) {
            panel.child(builder.makeFluidSlot(i, layout.exportFluids(), true)
                    .overlay(GTGuiTextures.CRACKING_OVERLAY_2)
                    .pos(106 + 18 * i, 24));
        }
        return panel;
    }

    @Override
    public ModularUI.Builder createJeiUITemplate(IItemHandlerModifiable importItems, IItemHandlerModifiable exportItems,
                                                 FluidTankList importFluids, FluidTankList exportFluids, int yOffset) {
        ModularUI.Builder builder = ModularUI.defaultBuilder(yOffset);
        if (recipeMap().getMaxInputs() == 1) {
            addSlot(builder, 52, 24 + yOffset, 0, importItems, importFluids, false, false);
        } else {
            int[] grid = determineSlotsGrid(recipeMap().getMaxInputs());
            for (int y = 0; y < grid[1]; y++) {
                for (int x = 0; x < grid[0]; x++) {
                    addSlot(builder, 34 + (x * 18) - (Math.max(0, grid[0] - 2) * 18),
                            24 + (y * 18) - (Math.max(0, grid[1] - 1) * 18) + yOffset,
                            y * grid[0] + x, importItems, importFluids, false, false);
                }
            }
        }

        addInventorySlotGroup(builder, exportItems, exportFluids, true, yOffset);
        addSlot(builder, 52, 24 + yOffset + 19 + 18, 0, importItems, importFluids, true, false);
        addSlot(builder, 34, 24 + yOffset + 19 + 18, 1, importItems, importFluids, true, false);

        Pair<DoubleSupplier, DoubleSupplier> suppliers = GTUtility.createPairedSupplier(200, 41, 0.5);
        builder.widget(new RecipeProgressWidget(suppliers.getLeft(), 42, 24 + yOffset + 18, 21, 19,
                GuiTextures.PROGRESS_BAR_CRACKING_INPUT, ProgressWidget.MoveType.VERTICAL, recipeMap()));
        builder.widget(new RecipeProgressWidget(suppliers.getRight(), 78, 23 + yOffset, 20, 20, progressBarTexture(),
                progressBarMoveType(), recipeMap()));
        return builder;
    }
}
