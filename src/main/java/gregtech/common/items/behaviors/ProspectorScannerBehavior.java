package gregtech.common.items.behaviors;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.widget.prospector.ProspectorMode;
import gregtech.common.mui.widget.prospector.OreListWidget;
import gregtech.common.mui.widget.prospector.ProspectorMapWidget;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public class ProspectorScannerBehavior implements IItemBehaviour, ItemUIFactory {

    private static final long VOLTAGE_FACTOR = 16L;
    private static final int FLUID_PROSPECTION_THRESHOLD = GTValues.HV;
    private static final int CARDINAL_COLOR = 0xFFAAAAAA;

    private final int radius;
    private final int tier;

    public ProspectorScannerBehavior(int radius, int tier) {
        this.radius = radius + 1;
        this.tier = tier;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(@NotNull World world, @NotNull EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote) {
            if (player.isSneaking()) {
                ProspectorMode mode = getMode(heldItem);
                ProspectorMode nextMode = mode.next();
                if (nextMode == ProspectorMode.FLUID) {
                    if (tier >= FLUID_PROSPECTION_THRESHOLD) {
                        setMode(heldItem, nextMode);
                        player.sendStatusMessage(new TextComponentTranslation("metaitem.prospector.mode.fluid"), true);
                    }
                } else {
                    setMode(heldItem, nextMode);
                    player.sendStatusMessage(new TextComponentTranslation("metaitem.prospector.mode.ores"), true);
                }
            } else if (checkCanUseScanner(heldItem, player, true)) {
                MetaItemGuiFactory.open(player, hand);
            } else {
                player.sendMessage(new TextComponentTranslation("behavior.prospector.not_enough_energy"));
            }
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    @NotNull
    private static ProspectorMode getMode(ItemStack stack) {
        if (stack == ItemStack.EMPTY) {
            return ProspectorMode.ORE;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return ProspectorMode.ORE;
        }
        if (tag.hasKey("Mode", Constants.NBT.TAG_INT)) {
            return ProspectorMode.VALUES[tag.getInteger("Mode")];
        }
        return ProspectorMode.ORE;
    }

    private static void setMode(ItemStack stack, @NotNull ProspectorMode mode) {
        NBTTagCompound tagCompound = GTUtility.getOrCreateNbtCompound(stack);
        tagCompound.setInteger("Mode", mode.ordinal());
    }

    private boolean checkCanUseScanner(ItemStack stack, @NotNull EntityPlayer player, boolean simulate) {
        return player.isCreative() || drainEnergy(stack, GTValues.V[tier] / VOLTAGE_FACTOR, simulate);
    }

    private static boolean drainEnergy(@NotNull ItemStack stack, long amount, boolean simulate) {
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;

        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    /**
     * Pays for one more tick of scanning. Returns false when the scanner ran dry, which closes the UI.
     */
    private Predicate<EntityPlayer> powerCheck(EnumHand hand) {
        return player -> {
            if (player.isCreative()) return true;
            ItemStack stack = player.getHeldItem(hand);
            if (!checkCanUseScanner(stack, player, true)) return false;
            drainEnergy(stack, GTValues.V[tier] / VOLTAGE_FACTOR, false);
            return true;
        };
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ProspectorMode mode = getMode(guiData.getUsedItemStack());
        ModularPanel panel = GTGuis.createPanel(guiData.getUsedItemStack(), 332, 200);

        OreListWidget oreList = new OreListWidget();
        oreList.pos(32 * radius - 6, 18)
                .size(332 - 32 * radius, 176);

        // the map widget wires itself into the ore list, so it has to be built after it
        ProspectorMapWidget map = new ProspectorMapWidget(radius, mode, 1, oreList, powerCheck(guiData.getHand()));
        map.pos(6, 18);

        int mapSize = 16 * (radius * 2 - 1);
        return panel
                .child(IKey.lang(getTranslationKey()).asWidget().pos(6, 6))
                .child(map)
                .child(oreList)
                .child(cardinal("N", 3 + mapSize / 2, 14))
                .child(cardinal("S", 3 + mapSize / 2, 14 + mapSize))
                .child(cardinal("W", 3, 15 + mapSize / 2))
                .child(cardinal("E", 3 + mapSize, 15 + mapSize / 2));
    }

    private static com.cleanroommc.modularui.widgets.TextWidget<?> cardinal(String letter, int x, int y) {
        return IKey.str(letter).color(CARDINAL_COLOR).shadow(true).asWidget().pos(x, y);
    }

    private String getTranslationKey() {
        return String.format("metaitem.prospector.%s.name", GTValues.VN[tier].toLowerCase(Locale.ROOT));
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        IItemBehaviour.super.addInformation(itemStack, lines);

        if (tier >= FLUID_PROSPECTION_THRESHOLD) {
            lines.add(I18n.format("metaitem.prospector.tooltip.fluids", radius));
            lines.add(I18n.format(getMode(itemStack).unlocalizedName));
        } else {
            lines.add(I18n.format("metaitem.prospector.tooltip.ores", radius));
        }
    }
}
