package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ImageWidget;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.RecipeProgressWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.util.GTUtility;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.apache.commons.lang3.tuple.Pair;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

@ApiStatus.Internal
public class ResearchStationUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /** The layout is drawn at fixed coordinates, so the panel is the size the layout was drawn for. */
    private static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    /**
     * Where the scanning bar hands over to the writing bar: the station spends three quarters of a recipe scanning
     * the item, then writes the result onto the data stick.
     */
    private static final double SPLIT = 0.75;

    public ResearchStationUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, true, true, false);
        setItemSlotOverlay(GuiTextures.SCANNER_OVERLAY, false);
        setItemSlotOverlay(GuiTextures.RESEARCH_STATION_OVERLAY, true);
        setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressWidget.MoveType.HORIZONTAL);
    }

    /**
     * The station scans an item into a data stick, which the default input block / output block grid says nothing
     * about: the two inputs sit either side of the scanner image and feed two chained progress bars. This is the
     * MUI1 {@link #createJeiUITemplate} layout, widget for widget.
     */
    @Override
    public ModularPanel constructRecipeViewerPanel(@NotNull String name, @NotNull RecipeViewerLayout layout) {
        PanelBuilder builder = new PanelBuilder().slotListener(layout.slotListener());
        ModularPanel panel = GTGuis.createPanel(name, PANEL_WIDTH, PANEL_HEIGHT);
        SlotGroup inputs = new SlotGroup("input_items", 2, 1, true);

        panel.child(new Widget<>()
                .pos(10, 0)
                .size(84, 60)
                .background(GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_BASE));
        panel.child(new RecipeProgressWidget()
                .recipeMap(recipeMap())
                .pos(72, 28)
                .size(54, 5)
                .value(viewerProgress(progressUpTo(layout.progress(), SPLIT)))
                .texture(GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_1, 54)
                .direction(Direction.RIGHT));
        panel.child(new RecipeProgressWidget()
                .recipeMap(recipeMap())
                .pos(119, 32)
                .size(10, 18)
                .value(viewerProgress(progressAfter(layout.progress(), SPLIT)))
                .texture(GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_2, 18)
                .direction(Direction.DOWN));

        panel.child(builder.makeItemSlot(inputs, 1, layout.importItems(), false)
                .overlay(GTGuiTextures.SCANNER_OVERLAY)
                .pos(43, 21));
        panel.child(builder.makeItemSlot(inputs, 0, layout.importItems(), false)
                .overlay(GTGuiTextures.RESEARCH_STATION_OVERLAY)
                .pos(97, 21));
        panel.child(builder.makeItemSlot(new SlotGroup("output_items", 1, 1, false), 0, layout.exportItems(), true)
                .overlay(GTGuiTextures.DATA_ORB_OVERLAY)
                .pos(115, 50));
        return panel;
    }

    @Override
    @NotNull
    public ModularUI.Builder createJeiUITemplate(IItemHandlerModifiable importItems, IItemHandlerModifiable exportItems,
                                                 FluidTankList importFluids, FluidTankList exportFluids, int yOffset) {
        Pair<DoubleSupplier, DoubleSupplier> pairedSuppliers = GTUtility.createPairedSupplier(200, 90, 0.75);
        return ModularUI.builder(GuiTextures.BACKGROUND, 176, 166)
                .widget(new ImageWidget(10, 0, 84, 60, GuiTextures.PROGRESS_BAR_RESEARCH_STATION_BASE))
                .widget(new ProgressWidget(pairedSuppliers.getLeft(), 72, 28, 54, 5,
                        GuiTextures.PROGRESS_BAR_RESEARCH_STATION_1, ProgressWidget.MoveType.HORIZONTAL))
                .widget(new ProgressWidget(pairedSuppliers.getRight(), 119, 32, 10, 18,
                        GuiTextures.PROGRESS_BAR_RESEARCH_STATION_2, ProgressWidget.MoveType.VERTICAL_DOWNWARDS))
                .widget(new SlotWidget(exportItems, 0, 115, 50, true, true)
                        .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.DATA_ORB_OVERLAY))
                .widget(new SlotWidget(importItems, 1, 43, 21, true, true)
                        .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.SCANNER_OVERLAY))
                .widget(new SlotWidget(importItems, 0, 97, 21, true, true)
                        .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.RESEARCH_STATION_OVERLAY));
    }
}
