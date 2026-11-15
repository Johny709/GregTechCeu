package gregtech.common.items.behaviors;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ClickButtonWidget;
import gregtech.api.gui.widgets.ImageCycleButtonWidget;
import gregtech.api.gui.widgets.SimpleTextWidget;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.gui.PlayerInventoryHolder;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntityClipboard;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import codechicken.lib.raytracer.RayTracer;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import java.util.function.Supplier;

import static gregtech.common.metatileentities.MetaTileEntities.CLIPBOARD_TILE;

public class ClipboardBehavior implements IItemBehaviour, ItemUIFactory {

    public static final int MAX_PAGES = 25;
    public static final int TASKS_PER_PAGE = 8;
    private static final int BUTTON_STATES = 4;
    private static final int TEXT_COLOR = GTGuiTheme.Colors.CLIPBOARD_TEXT;

    @Override
    public GTGuiTheme getUITheme() {
        return GTGuiTheme.CLIPBOARD;
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ItemStack held = guiData.getUsedItemStack();
        return createPanel(held, () -> guiData.getPlayer().getHeldItem(guiData.getHand()), guiSyncManager,
                () -> guiData.getPlayer().inventory.markDirty());
    }

    /**
     * Builds the editable clipboard panel. Used both for the clipboard held in hand and for the clipboard placed on a
     * wall, which is why the edited stack is passed as a supplier instead of directly.
     *
     * @param panelStack the stack the panel is named after
     * @param clipboard  supplies the stack whose NBT is edited. Its tag is mutated in place, so that the fake in-world
     *                   gui, which holds on to the same stack, keeps rendering the current page
     * @param onChange   run on the server after every edit, to persist the new NBT
     */
    public static ModularPanel createPanel(ItemStack panelStack, Supplier<ItemStack> clipboard,
                                           PanelSyncManager syncManager, Runnable onChange) {
        initNBT(clipboard.get());

        IntSyncValue pageNum = new IntSyncValue(() -> getPageNum(clipboard.get()));
        syncManager.syncValue("page_index", pageNum);

        ModularPanel panel = GTGuis.createPanel(panelStack, 186, 263)
                .background(GTGuiTextures.CLIPBOARD_BACKGROUND);

        panel.child(GTGuiTextures.CLIPBOARD_TEXT_BOX.asWidget()
                .pos(28, 28)
                .size(130, 12))
                .child(new TextFieldWidget()
                        .pos(30, 30)
                        .size(126, 9)
                        .setMaxLength(25)
                        .setTextColor(TEXT_COLOR)
                        .setTextAlignment(Alignment.Center)
                        .value(new StringSyncValue(() -> getTitle(clipboard.get()), title -> {
                            setTitle(clipboard.get(), title);
                            onChange.run();
                        })));

        for (int i = 0; i < TASKS_PER_PAGE; i++) {
            int task = i;
            CycleButtonWidget stateButton = new CycleButtonWidget()
                    .pos(14, 55 + 22 * task)
                    .size(15, 15)
                    .stateCount(BUTTON_STATES)
                    .value(new IntSyncValue(() -> getButtonState(clipboard.get(), task), state -> {
                        setButton(clipboard.get(), task, state);
                        onChange.run();
                    }));
            for (int state = 0; state < BUTTON_STATES; state++) {
                stateButton.stateBackground(state, GTGuiTextures.CLIPBOARD_BUTTON[state]);
            }

            panel.child(stateButton)
                    .child(GTGuiTextures.CLIPBOARD_TEXT_BOX.asWidget()
                            .pos(32, 58 + 22 * task)
                            .size(140, 12))
                    .child(new TextFieldWidget()
                            .pos(34, 60 + 22 * task)
                            .size(136, 9)
                            .setMaxLength(23)
                            .setTextColor(TEXT_COLOR)
                            .value(new StringSyncValue(() -> getString(clipboard.get(), task), text -> {
                                setString(clipboard.get(), task, text);
                                onChange.run();
                            })));
        }

        return panel.child(pageButton(GTGuiTextures.BUTTON_LEFT, 38, clipboard, -1, onChange))
                .child(pageButton(GTGuiTextures.BUTTON_RIGHT, 132, clipboard, 1, onChange))
                .child(IKey.dynamic(() -> (pageNum.getIntValue() + 1) + " / " + MAX_PAGES)
                        .asWidget()
                        .pos(0, 236)
                        .size(186, 9)
                        .alignment(Alignment.Center)
                        .color(TEXT_COLOR));
    }

    /** The read only variant rendered on the clipboard hanging on a wall. Still legacy MUI. */
    public static ModularUI createMTEUI(PlayerInventoryHolder holder, EntityPlayer entityPlayer) {
        initNBT(holder.getCurrentItem());
        ModularUI.Builder builder = ModularUI.builder(GuiTextures.CLIPBOARD_PAPER_BACKGROUND, 170, 238);

        builder.image(18, 8, 130, 14, GuiTextures.CLIPBOARD_TEXT_BOX);
        builder.widget(new SimpleTextWidget(20, 10, "", TEXT_COLOR, () -> getTitle(holder.getCurrentItem()), true)
                .setCenter(false));

        for (int i = 0; i < TASKS_PER_PAGE; i++) {
            int task = i;
            builder.widget(new ImageCycleButtonWidget(6, 37 + 20 * task, 15, 15, GuiTextures.CLIPBOARD_BUTTON,
                    BUTTON_STATES, () -> getButtonState(holder.getCurrentItem(), task),
                    state -> setButton(holder.getCurrentItem(), task, state)));
            builder.image(22, 38 + 20 * task, 140, 12, GuiTextures.CLIPBOARD_TEXT_BOX);
            builder.widget(new SimpleTextWidget(24, 40 + 20 * task, "", TEXT_COLOR,
                    () -> getString(holder.getCurrentItem(), task), true).setCenter(false));
        }

        builder.widget(new ClickButtonWidget(30, 200, 16, 16, "",
                click -> incrPageNum(holder.getCurrentItem(), click.isShiftClick ? -10 : -1))
                        .setButtonTexture(GuiTextures.BUTTON_LEFT).setShouldClientCallback(true));
        builder.widget(new ClickButtonWidget(124, 200, 16, 16, "",
                click -> incrPageNum(holder.getCurrentItem(), click.isShiftClick ? 10 : 1))
                        .setButtonTexture(GuiTextures.BUTTON_RIGHT).setShouldClientCallback(true));
        builder.widget(new SimpleTextWidget(85, 208, "", TEXT_COLOR,
                () -> (getPageNum(holder.getCurrentItem()) + 1) + " / " + MAX_PAGES, true));

        builder.shouldColor(false);
        return builder.build(holder, entityPlayer);
    }

    private static ButtonWidget<?> pageButton(UITexture texture, int x, Supplier<ItemStack> clipboard, int step,
                                              Runnable onChange) {
        ButtonWidget<?> button = new ButtonWidget<>();
        button.pos(x, 231)
                .size(16, 16)
                .background(texture)
                // a click is handled before the panel drops the focus, so a text field would commit its content
                // only after the page was turned, writing it to the page that was switched to. Commit it here.
                .onMousePressed(mouseButton -> {
                    button.getContext().removeFocus();
                    return false; // let the sync handler turn the page
                })
                .syncHandler(new InteractionSyncHandler()
                        .setOnMousePressed(data -> {
                            incrPageNum(clipboard.get(), data.shift ? step * 10 : step);
                            onChange.run();
                        }));
        return button;
    }

    private static NBTTagCompound getPageCompound(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack)) return null;
        short pageNum = stack.getTagCompound().getShort("PageIndex");
        return stack.getTagCompound().getCompoundTag("Page" + pageNum);
    }

    private static void setPageCompound(ItemStack stack, NBTTagCompound pageCompound) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        short pageNum = stack.getTagCompound().getShort("PageIndex");
        stack.getTagCompound().setTag("Page" + pageNum, pageCompound);
    }

    private static void initNBT(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
            tagCompound.setShort("PageIndex", (short) 0);
            tagCompound.setShort("TotalPages", (short) 0);

            NBTTagCompound pageCompound = new NBTTagCompound();
            pageCompound.setShort("ButStat", (short) 0);
            pageCompound.setString("Title", "");
            for (int i = 0; i < TASKS_PER_PAGE; i++) {
                pageCompound.setString("Task" + i, "");
            }

            for (int i = 0; i < MAX_PAGES; i++) {
                tagCompound.setTag("Page" + i, pageCompound.copy());
            }

            stack.setTagCompound(tagCompound);
        }
    }

    private static void setButton(ItemStack stack, int pos, int newState) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = getPageCompound(stack);
        short buttonState;
        buttonState = tagCompound.getShort("ButStat");

        short clearedState = (short) (buttonState & ~(3 << (pos * 2))); // Clear out the desired slot
        buttonState = (short) (clearedState | (newState << (pos * 2))); // And add the new state back in

        tagCompound.setShort("ButStat", buttonState);
        setPageCompound(stack, tagCompound);
    }

    private static int getButtonState(ItemStack stack, int pos) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return 0;
        NBTTagCompound tagCompound = getPageCompound(stack);
        short buttonState;
        buttonState = tagCompound.getShort("ButStat");
        return ((buttonState >> pos * 2) & 3);
    }

    private static void setString(ItemStack stack, int pos, String newString) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = getPageCompound(stack);
        tagCompound.setString("Task" + pos, newString);
        setPageCompound(stack, tagCompound);
    }

    private static String getString(ItemStack stack, int pos) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return "";
        NBTTagCompound tagCompound = getPageCompound(stack);
        return tagCompound.getString("Task" + pos);
    }

    private static void setTitle(ItemStack stack, String newString) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = getPageCompound(stack);
        assert tagCompound != null;
        tagCompound.setString("Title", newString);
        setPageCompound(stack, tagCompound);
    }

    private static String getTitle(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return "";
        NBTTagCompound tagCompound = getPageCompound(stack);
        return tagCompound.getString("Title");
    }

    private static int getPageNum(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return 1;
        NBTTagCompound tagCompound = stack.getTagCompound();
        return tagCompound.getInteger("PageIndex");
    }

    private static void incrPageNum(ItemStack stack, int increment) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = stack.getTagCompound();
        assert tagCompound != null;

        int currentIndex = tagCompound.getInteger("PageIndex");
        // Clamps currentIndex between 0 and MAX_PAGES.
        tagCompound.setInteger("PageIndex", Math.max(Math.min(currentIndex + increment, MAX_PAGES - 1), 0));
        stack.setTagCompound(tagCompound);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote && RayTracer.retrace(player).typeOfHit != RayTraceResult.Type.BLOCK) { // So that the player
                                                                                                  // doesn't place a
                                                                                                  // clipboard before
                                                                                                  // suddenly getting
                                                                                                  // the GUI
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    @Override
    public ActionResult<ItemStack> onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                             EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && facing.getAxis() != EnumFacing.Axis.Y) {
            ItemStack heldItem = player.getHeldItem(hand).copy();
            heldItem.setCount(1); // don't place multiple items at a time
            // Make sure it's the right block
            IBlockState testState = world.getBlockState(pos);
            Block testBlock = testState.getBlock();
            if (!testBlock.isAir(world.getBlockState(pos), world, pos) && testState.isSideSolid(world, pos, facing)) {
                // Step away from the block so that you don't replace it, and then give it our fun blockstate
                BlockPos shiftedPos = pos.offset(facing);
                Block shiftedBlock = world.getBlockState(shiftedPos).getBlock();
                if (shiftedBlock.isAir(world.getBlockState(shiftedPos), world, shiftedPos)) {
                    IBlockState state = GregTechAPI.mteManager.getRegistry(GTValues.MODID).getBlock().getDefaultState();
                    world.setBlockState(shiftedPos, state);
                    // Get new TE
                    shiftedBlock.createTileEntity(world, state);
                    // And manipulate it to our liking
                    IGregTechTileEntity holder = (IGregTechTileEntity) world.getTileEntity(shiftedPos);
                    if (holder != null) {
                        MetaTileEntityClipboard clipboard = (MetaTileEntityClipboard) holder
                                .setMetaTileEntity(CLIPBOARD_TILE);
                        if (clipboard != null) {
                            clipboard.initializeClipboard(heldItem);
                            clipboard.setFrontFacing(facing.getOpposite());
                            ItemStack returnedStack = player.getHeldItem(hand);
                            if (!player.isCreative()) {
                                returnedStack.setCount(player.getHeldItem(hand).getCount() - 1);
                            }
                            return ActionResult.newResult(EnumActionResult.SUCCESS, returnedStack);
                        }
                    }
                }
            }
        }
        return ActionResult.newResult(EnumActionResult.PASS, player.getHeldItem(hand));
    }
}
