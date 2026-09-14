package gregtech.common.mui.widget.prospector;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.common.gui.widget.prospector.ProspectingTexture;
import gregtech.common.gui.widget.prospector.ProspectorMode;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.FluidDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The list of everything the prospector has found so far. Entries arrive from the server one chunk at a time, so the
 * list grows while it is open; {@link ListWidget} schedules the resize itself on every added child.
 * <p>
 * Selecting an entry filters the map down to that one resource. The list is purely client side, its content is derived
 * from the scan packets the client receives anyway.
 */
public class OreListWidget extends ListWidget<IWidget, OreListWidget> {

    private static final int ROW_HEIGHT = 18;
    private static final int SELECTION_COLOR = 0x4BFFFFFF;
    private static final int ICON_UPDATE_INTERVAL = 20;

    /** Resource key (ore dict name or fluid name) to display name. */
    private final Map<String, String> ores = new HashMap<>();
    /** Resource keys in display order, matching the child order after the leading "all" row. */
    private final List<String> order = new ArrayList<>();
    private final List<OreRow> cyclingRows = new ArrayList<>();

    private String selected = ProspectingTexture.SELECTED_ALL;
    private @Nullable Consumer<String> onSelected;
    private int tickCounter;

    public OreListWidget() {
        OreRow all = new OreRow(ProspectingTexture.SELECTED_ALL);
        all.child(GTGuiTextures.RECIPE_LOCK.asWidget().size(18, 18).pos(0, 0));
        all.child(IKey.lang("terminal.prospector.list").color(0xFFFFFFFF).asWidget().pos(20, 5).height(9));
        addChild(all, 0);
    }

    public OreListWidget onSelected(Consumer<String> onSelected) {
        this.onSelected = onSelected;
        return this;
    }

    public String getSelected() {
        return this.selected;
    }

    public Map<String, String> getOres() {
        return this.ores;
    }

    public void setSelected(String key) {
        if (this.selected.equals(key)) return;
        this.selected = key;
        if (this.onSelected != null) {
            this.onSelected.accept(key);
        }
    }

    public void addOres(Set<String> keys, ProspectorMode mode) {
        switch (mode) {
            case ORE -> keys.stream().sorted().forEach(this::addOre);
            case FLUID -> keys.stream().sorted().forEach(this::addFluid);
        }
    }

    private void addOre(String oreDictName) {
        if (this.ores.containsKey(oreDictName)) return;
        ItemStack itemStack = OreDictUnifier.get(oreDictName);
        if (itemStack == null || itemStack.isEmpty()) return;

        this.ores.put(oreDictName, itemStack.getDisplayName());
        MaterialStack materialStack = OreDictUnifier.getMaterial(itemStack);
        int color = materialStack == null ? oreDictName.hashCode() :
                materialStack.material.getMaterialRGB() | 0xFF000000;

        OreRow row = new OreRow(oreDictName);
        row.icon = new ItemDrawable(itemStack);
        row.child(row.icon.asWidget().size(18, 18).pos(0, 0));
        row.child(IKey.str(itemStack.getDisplayName()).color(color).asWidget().pos(20, 5).height(9));
        this.cyclingRows.add(row);
        insertSorted(oreDictName, row);
    }

    private void addFluid(String fluidName) {
        if (this.ores.containsKey(fluidName)) return;
        FluidStack fluidStack = FluidRegistry.getFluidStack(fluidName, 1);
        if (fluidStack == null) return;

        this.ores.put(fluidName, fluidStack.getLocalizedName());

        OreRow row = new OreRow(fluidName);
        row.child(new FluidDrawable(fluidStack).asWidget().size(18, 18).pos(0, 0));
        row.child(IKey.str(fluidStack.getLocalizedName())
                .color(getFluidColor(fluidStack.getFluid()))
                .asWidget().pos(20, 5).height(9));
        insertSorted(fluidName, row);
    }

    /** Keeps the rows sorted by resource key, with the "all" row pinned to the top. */
    private void insertSorted(String key, OreRow row) {
        int index = 0;
        while (index < this.order.size() && this.order.get(index).compareTo(key) < 0) {
            index++;
        }
        this.order.add(index, key);
        addChild(row, index + 1);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // ore dict names can map to several blocks, cycle through them so all of them are shown
        if (++this.tickCounter % ICON_UPDATE_INTERVAL != 0) return;
        for (OreRow row : this.cyclingRows) {
            if (row.icon == null) continue;
            List<ItemStack> stacks = OreDictUnifier.getAllWithOreDictionaryName(row.key);
            if (stacks.isEmpty()) continue;
            row.icon.setItem(stacks.get(Math.floorMod(this.tickCounter / ICON_UPDATE_INTERVAL, stacks.size())));
        }
    }

    public static int getFluidColor(Fluid fluid) {
        if (fluid == FluidRegistry.WATER) {
            return 3183823;
        }
        return fluid == FluidRegistry.LAVA ? 16766720 : fluid.getColor();
    }

    private class OreRow extends ParentWidget<OreRow> implements Interactable {

        private final String key;
        private @Nullable ItemDrawable icon;

        private OreRow(String key) {
            this.key = key;
            widthRel(1f).height(ROW_HEIGHT);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            if (OreListWidget.this.selected.equals(this.key)) {
                GuiDraw.drawRect(0, 0, getArea().w(), getArea().h(), SELECTION_COLOR);
            }
        }

        @Override
        public @NotNull Result onMousePressed(int mouseButton) {
            setSelected(this.key);
            return Result.SUCCESS;
        }
    }
}
