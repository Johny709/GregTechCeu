package gregtech.integration.jei.recipe;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.GregTechGuiScreen;
import gregtech.api.mui.fake.FakeModularScreen;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import gregtech.api.recipes.properties.impl.ResearchPropertyData;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.GTUtility;
import gregtech.api.util.LocalizationUtils;
import gregtech.common.ConfigHolder;
import gregtech.integration.jei.JustEnoughItemsModule;
import gregtech.integration.jei.utils.render.FluidStackTextRenderer;
import gregtech.integration.jei.utils.render.ItemStackTextRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.widget.sizer.Area;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RecipeMapCategory implements IRecipeCategory<GTRecipeWrapper> {

    /** How long one pass of the progress bar takes, in ticks, matching what the legacy jei layout used. */
    private static final int PROGRESS_CYCLE_TICKS = 200;
    /** Inset of the fluid render inside a fluid slot, so jei's fluid lines up with the one the slot would draw. */
    private static final int FLUID_RENDER_INSET = 1;
    /**
     * Lines of recipe info to keep free under the layout. {@link GTRecipeWrapper#drawInfo} writes its lines upwards
     * from the bottom of the category, so whatever is not reserved here is drawn over the slots - and the category is
     * left short enough for jei to stack the next recipe on top of this one. Three lines are the total EU, the EU/t
     * and the duration, which nearly every recipe shows, plus one for a property such as a temperature.
     */
    private static final int INFO_LINES = 4;

    private final RecipeMap<?> recipeMap;
    private final GTRecipeCategory category;
    private final ItemStackHandler importItems, exportItems;
    private final FluidTankList importFluids, exportFluids;

    /** The recipe map's own MUI2 layout, drawn detached from any machine. */
    private final FakeModularScreen screen;
    private final List<IWidget> itemInputs = new ArrayList<>();
    private final List<IWidget> itemOutputs = new ArrayList<>();
    private final List<IWidget> fluidInputs = new ArrayList<>();
    private final List<IWidget> fluidOutputs = new ArrayList<>();

    private final mezz.jei.api.gui.IDrawable backgroundDrawable;
    private Object iconIngredient;
    private mezz.jei.api.gui.IDrawable icon;

    private static final Map<GTRecipeCategory, RecipeMapCategory> gtCategories = new Object2ObjectOpenHashMap<>();
    private static final Map<RecipeMap<?>, List<RecipeMapCategory>> recipeMapCategories = new Object2ObjectOpenHashMap<>();

    public RecipeMapCategory(@NotNull RecipeMap<?> recipeMap, @NotNull GTRecipeCategory category,
                             IGuiHelper guiHelper) {
        this.recipeMap = recipeMap;
        this.category = category;

        FluidTank[] importFluidTanks = new FluidTank[recipeMap.getMaxFluidInputs()];
        for (int i = 0; i < importFluidTanks.length; i++)
            importFluidTanks[i] = new FluidTank(16000);
        FluidTank[] exportFluidTanks = new FluidTank[recipeMap.getMaxFluidOutputs()];
        for (int i = 0; i < exportFluidTanks.length; i++)
            exportFluidTanks[i] = new FluidTank(16000);

        // the assembly line shows its research data stick in an extra input slot
        this.importItems = new ItemStackHandler(
                recipeMap.getMaxInputs() + (recipeMap == RecipeMaps.ASSEMBLY_LINE_RECIPES ? 1 : 0));
        this.exportItems = new ItemStackHandler(recipeMap.getMaxOutputs());
        this.importFluids = new FluidTankList(false, importFluidTanks);
        this.exportFluids = new FluidTankList(false, exportFluidTanks);

        RecipeMapUI<?> ui = recipeMap.getRecipeMapUI();
        ModularPanel panel = ui.constructRecipeViewerPanel("jei." + category.getUniqueID(),
                new RecipeMapUI.RecipeViewerLayout(this.importItems, this.exportItems, this.importFluids,
                        this.exportFluids, RecipeMapCategory::animatedProgress, this::collectSlot));
        // jei draws the recipe on its own background, so the panel contributes only its widgets
        panel.background(IDrawable.EMPTY);

        this.screen = new FakeModularScreen(new GregTechGuiScreen(panel, GTGuiTheme.RECIPE_VIEWER), new UISettings());
        this.backgroundDrawable = guiHelper.createBlankDrawable(this.screen.getWidth(),
                contentHeight(panel) + ui.getPropertyHeightShift());

        gtCategories.put(category, this);
        recipeMapCategories.compute(recipeMap, (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(this);
            return v;
        });
    }

    /** Loops the progress bar off the world clock, the way the legacy jei layout animated it by itself. */
    private static double animatedProgress() {
        return (Minecraft.getSystemTime() / 50L % PROGRESS_CYCLE_TICKS) / (double) PROGRESS_CYCLE_TICKS;
    }

    private void collectSlot(IWidget slot, boolean isFluid, boolean isOutput, int index) {
        List<IWidget> slots = isFluid ? (isOutput ? this.fluidOutputs : this.fluidInputs) :
                (isOutput ? this.itemOutputs : this.itemInputs);
        // the builder creates slots in index order, but pad defensively so index and position never drift apart
        while (slots.size() <= index) slots.add(null);
        slots.set(index, slot);
    }

    /**
     * The height the category needs: what the layout actually occupies, plus room for the recipe info under it. The
     * panel's own height is no use here - it is as tall as the machine gui it was drawn for, player inventory and all.
     */
    private static int contentHeight(ModularPanel panel) {
        int bottom = 0;
        // the panel itself is skipped, only what it holds counts
        for (IWidget child : panel.getChildren()) {
            bottom = Math.max(bottom, widgetBottom(child));
        }
        return bottom + INFO_LINES * GTRecipeWrapper.LINE_HEIGHT;
    }

    /** @return the lowest edge of this widget or any of its children, in panel coordinates */
    private static int widgetBottom(IWidget widget) {
        if (!widget.isEnabled()) return 0;
        int bottom = widget.getArea().ey();
        for (IWidget child : widget.getChildren()) {
            bottom = Math.max(bottom, widgetBottom(child));
        }
        return bottom;
    }

    @Override
    @NotNull
    public String getUid() {
        return category.getUniqueID();
    }

    @Override
    @NotNull
    public String getTitle() {
        return LocalizationUtils.format(category.getTranslationKey());
    }

    @Nullable
    @Override
    public mezz.jei.api.gui.IDrawable getIcon() {
        if (icon != null) {
            return icon;
        } else if (iconIngredient instanceof mezz.jei.api.gui.IDrawable drawable) {
            return icon = drawable;
        } else if (iconIngredient != null) {
            // cache the icon drawable for less gc pressure
            return icon = JustEnoughItemsModule.guiHelper.createDrawableIngredient(iconIngredient);
        }
        // JEI will automatically populate the icon as the first registered catalyst if null
        return null;
    }

    public void setIcon(Object icon) {
        if (iconIngredient == null) {
            iconIngredient = icon;
        }
    }

    @Override
    @NotNull
    public String getModName() {
        return GTValues.MODID;
    }

    @Override
    @NotNull
    public mezz.jei.api.gui.IDrawable getBackground() {
        return backgroundDrawable;
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @NotNull GTRecipeWrapper recipeWrapper,
                          @NotNull IIngredients ingredients) {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        IGuiFluidStackGroup fluidStackGroup = recipeLayout.getFluidStacks();

        for (int i = 0; i < this.itemInputs.size(); i++) {
            IWidget slot = this.itemInputs.get(i);
            if (slot == null) continue;
            Area area = slot.getArea();
            itemStackGroup.init(i, true,
                    new ItemStackTextRenderer(recipeWrapper.isNotConsumedItem(i)),
                    area.x + 1, area.y + 1, area.width - 2, area.height - 2, 0, 0);
        }

        int outputCount = recipeWrapper.getRecipe().getOutputs().size();
        for (int i = 0; i < this.itemOutputs.size(); i++) {
            IWidget slot = this.itemOutputs.get(i);
            if (slot == null) continue;
            Area area = slot.getArea();
            itemStackGroup.init(this.importItems.getSlots() + i, false,
                    new ItemStackTextRenderer(recipeWrapper.getOutputChance(i - outputCount),
                            recipeWrapper.getChancedOutputLogic()),
                    area.x + 1, area.y + 1, area.width - 2, area.height - 2, 0, 0);
        }

        List<List<FluidStack>> fluidInputsList = ingredients.getInputs(VanillaTypes.FLUID);
        for (int i = 0; i < this.fluidInputs.size(); i++) {
            IWidget slot = this.fluidInputs.get(i);
            if (slot == null) continue;
            Area area = slot.getArea();
            int amount = 0;
            if (fluidInputsList.size() > i && !fluidInputsList.get(i).isEmpty()) {
                amount = fluidInputsList.get(i).get(0).amount;
            }
            int w = area.width - 2 * FLUID_RENDER_INSET;
            int h = area.height - 2 * FLUID_RENDER_INSET;
            fluidStackGroup.init(i, true,
                    new FluidStackTextRenderer(amount, false, w, h, null)
                            .setNotConsumed(recipeWrapper.isNotConsumedFluid(i)),
                    area.x + FLUID_RENDER_INSET, area.y + FLUID_RENDER_INSET, w, h, 0, 0);
        }

        List<List<FluidStack>> fluidOutputsList = ingredients.getOutputs(VanillaTypes.FLUID);
        int fluidOutputCount = recipeWrapper.getRecipe().getFluidOutputs().size();
        for (int i = 0; i < this.fluidOutputs.size(); i++) {
            IWidget slot = this.fluidOutputs.get(i);
            if (slot == null) continue;
            Area area = slot.getArea();
            int amount = 0;
            if (fluidOutputsList.size() > i && !fluidOutputsList.get(i).isEmpty()) {
                amount = fluidOutputsList.get(i).get(0).amount;
            }
            int w = area.width - 2 * FLUID_RENDER_INSET;
            int h = area.height - 2 * FLUID_RENDER_INSET;
            fluidStackGroup.init(this.importFluids.getTanks() + i, false,
                    new FluidStackTextRenderer(amount, false, w, h, null,
                            recipeWrapper.getFluidOutputChance(i - fluidOutputCount),
                            recipeWrapper.getChancedFluidOutputLogic()),
                    area.x + FLUID_RENDER_INSET, area.y + FLUID_RENDER_INSET, w, h, 0, 0);
        }

        if (ConfigHolder.machines.enableResearch && this.recipeMap == RecipeMaps.ASSEMBLY_LINE_RECIPES) {
            ResearchPropertyData data = recipeWrapper.getRecipe().getProperty(ResearchProperty.getInstance(), null);
            if (data != null) {
                List<ItemStack> dataItems = new ArrayList<>();
                for (ResearchPropertyData.ResearchEntry entry : data) {
                    ItemStack dataStick = entry.dataItem().copy();
                    AssemblyLineManager.writeResearchToNBT(GTUtility.getOrCreateNbtCompound(dataStick),
                            entry.researchId());
                    dataItems.add(dataStick);
                }
                itemStackGroup.set(16, dataItems);
            }
        }

        itemStackGroup.addTooltipCallback(recipeWrapper::addItemTooltip);
        fluidStackGroup.addTooltipCallback(recipeWrapper::addFluidTooltip);
        itemStackGroup.set(ingredients);
        fluidStackGroup.set(ingredients);
    }

    @Override
    public void drawExtras(@NotNull Minecraft minecraft) {
        this.screen.updateScreen();
        // jei owns the mouse here and draws every ingredient itself, so the layout is drawn without hover state
        this.screen.drawInGui(-1, -1, minecraft.getRenderPartialTicks());
    }

    @Nullable
    public static RecipeMapCategory getCategoryFor(@NotNull GTRecipeCategory category) {
        return gtCategories.get(category);
    }

    @Nullable
    public static Collection<RecipeMapCategory> getCategoriesFor(@NotNull RecipeMap<?> recipeMap) {
        return recipeMapCategories.get(recipeMap);
    }
}
