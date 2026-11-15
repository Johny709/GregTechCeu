package gregtech.common.mui.widget.prospector;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.util.FileUtility;
import gregtech.api.util.Mods;
import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.api.worldgen.filler.FillerEntry;
import gregtech.common.gui.widget.prospector.ProspectingTexture;
import gregtech.common.gui.widget.prospector.ProspectorMode;
import gregtech.core.network.packets.PacketProspecting;
import gregtech.integration.xaero.ColorUtility;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Draws the prospector's chunk map and turns a double click into a minimap waypoint.
 * <p>
 * The scan itself runs in {@link ProspectorScanSyncHandler}; this widget only consumes the resulting packets on the
 * client. The texture is created lazily on the first packet, which never arrives on the server, so no side checks are
 * needed anywhere else.
 */
public class ProspectorMapWidget extends Widget<ProspectorMapWidget> implements Interactable {

    private static final int CELL = 16;
    private static final int HOVER_COLOR = 0x4B6C6C6C;
    private static final long DOUBLE_CLICK_MS = 400;
    private static final int MAX_PACKETS_PER_TICK = 10;

    private final int chunkRadius;
    private final int diameter;
    private final ProspectorMode mode;
    private final ProspectorScanSyncHandler scanSyncHandler;
    private final @Nullable OreListWidget oreList;

    private final Queue<PacketProspecting> packetQueue = new LinkedBlockingQueue<>();
    private @Nullable ProspectingTexture texture;
    private @Nullable Consumer<PacketProspecting> onPacketReceived;
    private boolean darkMode = false;

    private long lastClicked;
    private final List<String> hoveredNames = new ArrayList<>();
    private final List<IKey> hoveredTooltip = new ArrayList<>();
    private int hoveredOreHeight = 0;
    private int color;

    public ProspectorMapWidget(int chunkRadius, @NotNull ProspectorMode mode, int scanTick,
                               @Nullable OreListWidget oreList, Predicate<EntityPlayer> powerCheck) {
        this.chunkRadius = chunkRadius;
        this.diameter = chunkRadius * 2 - 1;
        this.mode = mode;
        this.oreList = oreList;

        this.scanSyncHandler = new ProspectorScanSyncHandler(mode, chunkRadius, scanTick, powerCheck);
        setSyncHandler(this.scanSyncHandler);

        size(CELL * this.diameter, CELL * this.diameter);

        if (oreList != null) {
            oreList.onSelected(name -> {
                if (this.texture != null) {
                    this.texture.loadTexture(null, name);
                }
            });
        }
    }

    @Override
    public boolean isValidSyncHandler(SyncHandler syncHandler) {
        return syncHandler instanceof ProspectorScanSyncHandler;
    }

    @Override
    public void onInit() {
        // harmless on the server, where no scan packet is ever read back
        this.scanSyncHandler.setPacketConsumer(this.packetQueue::add);
        tooltip().setAutoUpdate(true);
        tooltip().tooltipBuilder(tooltip -> {
            tooltip.clearText();
            this.hoveredTooltip.forEach(tooltip::addLine);
        });
    }

    /**
     * Hook for addons which want to see every scan packet, kept from the MUI1 widget.
     */
    @SideOnly(Side.CLIENT)
    public void setOnPacketReceived(@Nullable Consumer<PacketProspecting> onPacketReceived) {
        this.onPacketReceived = onPacketReceived;
    }

    @SideOnly(Side.CLIENT)
    public void addPacketToQueue(PacketProspecting packet) {
        this.packetQueue.add(packet);
    }

    @SideOnly(Side.CLIENT)
    public void setDarkMode(boolean darkMode) {
        if (this.darkMode == darkMode) return;
        this.darkMode = darkMode;
        if (this.texture != null) {
            this.texture.loadTexture(null, darkMode);
        }
    }

    @SideOnly(Side.CLIENT)
    public boolean getDarkMode() {
        return this.darkMode;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        int budget = MAX_PACKETS_PER_TICK;
        while (budget-- > 0 && !this.packetQueue.isEmpty()) {
            PacketProspecting packet = this.packetQueue.poll();
            if (this.onPacketReceived != null) {
                this.onPacketReceived.accept(packet);
            }
            if (this.texture == null) {
                this.texture = new ProspectingTexture(packet.mode, this.chunkRadius, this.darkMode);
            }
            this.texture.updateTexture(packet);
            if (this.oreList != null) {
                this.oreList.addOres(packet.ores, packet.mode);
            }
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (this.texture == null) return;
        this.texture.draw(0, 0);

        if (!isHovering()) {
            this.hoveredTooltip.clear();
            this.hoveredNames.clear();
            return;
        }

        int cellX = context.getMouseX() / CELL;
        int cellZ = context.getMouseY() / CELL;
        if (cellX < 0 || cellZ < 0 || cellX >= this.diameter || cellZ >= this.diameter) {
            this.hoveredTooltip.clear();
            this.hoveredNames.clear();
            return;
        }

        GuiDraw.drawRect(cellX * CELL, cellZ * CELL, CELL, CELL, HOVER_COLOR);
        collectHovered(cellX, cellZ);
    }

    /**
     * Recomputes what is under the cursor. Runs every frame while hovering so that both the tooltip and a click act on
     * the same data.
     */
    private void collectHovered(int cellX, int cellZ) {
        this.hoveredNames.clear();
        this.hoveredTooltip.clear();
        this.hoveredOreHeight = 0;
        if (this.texture == null) return;

        int[] maxAmount = { 0 };
        if (this.mode == ProspectorMode.ORE) {
            this.hoveredTooltip.add(IKey.lang("terminal.prospector.ore"));
            collectHoveredOres(cellX, cellZ, maxAmount);
        } else if (this.mode == ProspectorMode.FLUID) {
            this.hoveredTooltip.add(IKey.lang("terminal.prospector.fluid"));
            collectHoveredFluid(cellX, cellZ, maxAmount);
        }

        if (Mods.JourneyMap.isModLoaded() || Mods.VoxelMap.isModLoaded() || Mods.XaerosMinimap.isModLoaded()) {
            this.hoveredTooltip.add(IKey.lang("terminal.prospector.waypoint.add"));
        }
    }

    private void collectHoveredOres(int cellX, int cellZ, int[] maxAmount) {
        HashMap<String, Integer> oreInfo = new HashMap<>();
        HashMap<String, Integer> oreHeight = new HashMap<>();
        for (int i = 0; i < CELL; i++) {
            for (int j = 0; j < CELL; j++) {
                var column = this.texture.map[cellX * CELL + i][cellZ * CELL + j];
                if (column == null) continue;
                column.forEach((height, dict) -> {
                    String name = OreDictUnifier.get(dict).getDisplayName();
                    if (!ProspectingTexture.SELECTED_ALL.equals(this.texture.getSelected()) &&
                            !this.texture.getSelected().equals(dict)) {
                        return;
                    }
                    oreInfo.put(name, oreInfo.getOrDefault(name, 0) + 1);
                    oreHeight.put(name, oreHeight.getOrDefault(name, 0) + Byte.toUnsignedInt(height));
                    if (oreInfo.get(name) > maxAmount[0]) {
                        maxAmount[0] = oreInfo.get(name);
                        MaterialStack m = OreDictUnifier.getMaterial(OreDictUnifier.get(dict));
                        if (m != null) {
                            this.color = m.material.getMaterialRGB();
                        }
                    }
                });
            }
        }

        oreHeight.forEach((name, height) -> {
            this.hoveredOreHeight += height;
            int count = oreInfo.getOrDefault(name, 0);
            oreHeight.put(name, count != 0 ? height / count : 0);
        });
        int totalCount = oreInfo.values().stream().reduce(0, Integer::sum);
        if (totalCount != 0) {
            this.hoveredOreHeight /= totalCount;
        }
        oreInfo.forEach((name, count) -> {
            int height = oreHeight.getOrDefault(name, 0);
            this.hoveredTooltip.add(IKey.str(name + " --- §e" + count + "§r, §cy" + height + "§r"));
            this.hoveredNames.add(name);
        });
    }

    private void collectHoveredFluid(int cellX, int cellZ, int[] maxAmount) {
        var cell = this.texture.map[cellX][cellZ];
        if (cell == null || cell.isEmpty()) return;
        if (!ProspectingTexture.SELECTED_ALL.equals(this.texture.getSelected()) &&
                !this.texture.getSelected().equals(cell.get((byte) 1))) {
            return;
        }
        FluidStack fluidStack = FluidRegistry.getFluidStack(cell.get((byte) 1), 1);
        if (fluidStack == null) return;

        this.hoveredTooltip.add(IKey.lang("terminal.prospector.fluid.info",
                fluidStack.getLocalizedName(), cell.get((byte) 2), cell.get((byte) 3)));
        this.hoveredNames.add(fluidStack.getLocalizedName());
        int amount = Integer.parseInt(cell.get((byte) 2));
        if (amount > maxAmount[0]) {
            maxAmount[0] = amount;
            this.color = fluidStack.getFluid().getColor(fluidStack);
        }
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        return Result.ACCEPT;
    }

    @Override
    public @NotNull Result onMouseTapped(int mouseButton) {
        int cellX = getContext().getMouseX() / CELL;
        int cellZ = getContext().getMouseY() / CELL;
        if (cellX < 0 || cellZ < 0 || cellX >= this.diameter || cellZ >= this.diameter) {
            return Result.IGNORE;
        }

        long now = System.currentTimeMillis();
        boolean doubleClick = now - this.lastClicked < DOUBLE_CLICK_MS;
        this.lastClicked = now;
        if (!doubleClick || this.hoveredNames.isEmpty()) {
            return Result.SUCCESS;
        }

        int xDiff = cellX - (this.chunkRadius - 1);
        int zDiff = cellZ - (this.chunkRadius - 1);
        int xPos = ((Minecraft.getMinecraft().player.chunkCoordX + xDiff) << 4) + 8;
        int zPos = ((Minecraft.getMinecraft().player.chunkCoordZ + zDiff) << 4) + 8;
        int yPos = this.hoveredOreHeight != 0 ? this.hoveredOreHeight :
                Minecraft.getMinecraft().world.getHeight(xPos, zPos);
        BlockPos pos = new BlockPos(xPos, yPos, zPos);

        trimHoveredNames();
        boolean added = false;
        if (Mods.JourneyMap.isModLoaded()) {
            added = addJourneymapWaypoint(pos);
        } else if (Mods.VoxelMap.isModLoaded()) {
            added = addVoxelMapWaypoint(pos);
        } else if (Mods.XaerosMinimap.isModLoaded()) {
            added = addXaeroMapWaypoint(pos);
        }
        if (added) {
            Minecraft.getMinecraft().player
                    .sendStatusMessage(new TextComponentTranslation("behavior.prospector.added_waypoint"), true);
        }
        return Result.SUCCESS;
    }

    private void trimHoveredNames() {
        List<OreDepositDefinition> oreVeins = WorldGenRegistry.getOreDeposits();
        for (OreDepositDefinition odd : oreVeins) {
            for (FillerEntry fillerEntry : odd.getBlockFiller().getAllPossibleStates()) {
                Collection<String> matches = new ArrayList<>();
                Collection<IBlockState> pr = fillerEntry.getPossibleResults();
                for (IBlockState bs : pr) {
                    Set<String> ores = OreDictUnifier.getOreDictionaryNames(new ItemStack(bs.getBlock()));
                    for (String dict : ores) {
                        String name = OreDictUnifier.get(dict).getDisplayName();
                        if (this.hoveredNames.contains(name)) {
                            matches.add(name);
                        }
                    }
                }
                if (matches.size() > pr.size() / 2) {
                    this.hoveredNames.removeAll(matches);
                    this.hoveredNames.add(FileUtility.trimFileName(odd.getDepositName()));
                }
            }
        }
    }

    @NotNull
    private String createVeinName() {
        // remove the [] surrounding the array
        String s = this.hoveredNames.toString();
        return s.substring(1, s.length() - 1);
    }

    @Optional.Method(modid = Mods.Names.JOURNEY_MAP)
    private boolean addJourneymapWaypoint(BlockPos b) {
        journeymap.client.model.Waypoint journeyMapWaypoint = new journeymap.client.model.Waypoint(createVeinName(),
                b,
                new Color(this.color),
                journeymap.client.model.Waypoint.Type.Normal,
                Minecraft.getMinecraft().world.provider.getDimension());
        if (!journeymap.client.waypoint.WaypointStore.INSTANCE.getAll().contains(journeyMapWaypoint)) {
            journeymap.client.waypoint.WaypointStore.INSTANCE.save(journeyMapWaypoint);
            return true;
        }
        return false;
    }

    @Optional.Method(modid = Mods.Names.VOXEL_MAP)
    private boolean addVoxelMapWaypoint(@NotNull BlockPos b) {
        Color c = new Color(this.color);
        TreeSet<Integer> world = new TreeSet<>();
        world.add(Minecraft.getMinecraft().world.provider.getDimension());

        com.mamiyaotaru.voxelmap.interfaces.IWaypointManager waypointManager = com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap
                .getInstance().getWaypointManager();
        com.mamiyaotaru.voxelmap.util.Waypoint voxelMapWaypoint = new com.mamiyaotaru.voxelmap.util.Waypoint(
                createVeinName(),
                b.getX(),
                b.getZ(),
                b.getY(),
                true,
                c.getRed() / 255F,
                c.getGreen() / 255F,
                c.getBlue() / 255F,
                Minecraft.getMinecraft().world.provider.getDimensionType().getSuffix(),
                Minecraft.getMinecraft().world.provider.getDimensionType().getName(),
                world);

        if (!waypointManager.getWaypoints().contains(voxelMapWaypoint)) {
            waypointManager.addWaypoint(voxelMapWaypoint);
            waypointManager.saveWaypoints();
            return true;
        }
        return false;
    }

    @Optional.Method(modid = Mods.Names.XAEROS_MINIMAP)
    private boolean addXaeroMapWaypoint(@NotNull BlockPos b) {
        int red = clampColor(this.color >> 16 & 0xFF);
        int green = clampColor(this.color >> 8 & 0xFF);
        int blue = clampColor(this.color & 0xFF);

        Color wpc = new Color(red, green, blue);
        double[] labWPC = ColorUtility.getLab(wpc);
        int bestColorIndex = 0;
        double closestDistance = Double.MAX_VALUE;

        for (int i = 0; i < XAERO_COLORS.length; i++) {
            double[] c = XAERO_COLORS[i];
            double diffLInner = Math.abs(c[0] - labWPC[0]);
            double diffAInner = Math.abs(c[1] - labWPC[1]);
            double diffBInner = Math.abs(c[2] - labWPC[2]);
            double distance = diffLInner * diffLInner + diffAInner * diffAInner + diffBInner * diffBInner;
            if (distance < closestDistance) {
                closestDistance = distance;
                bestColorIndex = i;
            }
        }

        xaero.common.XaeroMinimapSession minimapSession = xaero.common.XaeroMinimapSession.getCurrentSession();
        xaero.common.minimap.waypoints.WaypointSet wps = minimapSession.getWaypointsManager().getWaypoints();
        xaero.common.minimap.waypoints.WaypointWorld ww = minimapSession.getWaypointsManager().getCurrentWorld();
        xaero.common.minimap.waypoints.Waypoint xaeroWaypoint = new xaero.common.minimap.waypoints.Waypoint(
                b.getX(),
                b.getY(),
                b.getZ(),
                createVeinName(), this.hoveredNames.get(0).substring(0, 1), bestColorIndex);

        for (xaero.common.minimap.waypoints.Waypoint xwp : wps.getList()) {
            if (xwp.getX() == xaeroWaypoint.getX() &&
                    xwp.getY() == xaeroWaypoint.getY() &&
                    xwp.getZ() == xaeroWaypoint.getZ()) {
                return false;
            }
        }
        wps.getList().add(xaeroWaypoint);
        try {
            minimapSession.getModMain().getSettings().saveWaypoints(ww);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private static int clampColor(int color) {
        if (color < 32) {
            return 0;
        } else if (color < 128) {
            return 128;
        } else if (color < 192) {
            return 192;
        } else {
            return 255;
        }
    }

    private static final double[][] XAERO_COLORS = {
            ColorUtility.getLab(new Color(0, 0, 0)),
            ColorUtility.getLab(new Color(0, 0, 128)),
            ColorUtility.getLab(new Color(0, 128, 0)),
            ColorUtility.getLab(new Color(0, 128, 128)),
            ColorUtility.getLab(new Color(128, 0, 0)),
            ColorUtility.getLab(new Color(128, 0, 128)),
            ColorUtility.getLab(new Color(128, 128, 0)),
            ColorUtility.getLab(new Color(192, 192, 192)),
            ColorUtility.getLab(new Color(128, 128, 128)),
            ColorUtility.getLab(new Color(0, 0, 255)),
            ColorUtility.getLab(new Color(0, 255, 0)),
            ColorUtility.getLab(new Color(0, 255, 255)),
            ColorUtility.getLab(new Color(255, 0, 0)),
            ColorUtility.getLab(new Color(255, 0, 255)),
            ColorUtility.getLab(new Color(255, 255, 0)),
            ColorUtility.getLab(new Color(255, 255, 255)),
    };
}
