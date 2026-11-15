package gregtech.common.mui.widget.prospector;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.ore.StoneType;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;
import gregtech.common.gui.widget.prospector.ProspectorMode;
import gregtech.core.network.packets.PacketProspecting;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Drives the prospector scan on the server and ships each scanned chunk to the client.
 * <p>
 * In MUI1 this lived in {@code WidgetProspectingMap#detectAndSendChanges} and pushed packets through the per-widget
 * update channel. MUI2 has no such channel, so the scan moved into a sync handler, which is ticked server side for
 * exactly as long as the panel is open.
 */
public class ProspectorScanSyncHandler extends SyncHandler {

    private static final int UPDATE_MAP = 0;

    private final ProspectorMode mode;
    private final int chunkRadius;
    private final int scanTick;
    /** Consumes power for one more tick of scanning, and reports whether there was enough. Server side only. */
    private final Predicate<EntityPlayer> powerCheck;

    private int chunkIndex = 0;

    private @Nullable Consumer<PacketProspecting> packetConsumer;

    public ProspectorScanSyncHandler(ProspectorMode mode, int chunkRadius, int scanTick,
                                     Predicate<EntityPlayer> powerCheck) {
        this.mode = mode;
        this.chunkRadius = chunkRadius;
        this.scanTick = scanTick;
        this.powerCheck = powerCheck;
    }

    /**
     * Called on the client for every chunk the server scanned.
     */
    public void setPacketConsumer(@Nullable Consumer<PacketProspecting> packetConsumer) {
        this.packetConsumer = packetConsumer;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        EntityPlayer player = getSyncManager().getPlayer();
        if (!this.powerCheck.test(player)) {
            if (player instanceof EntityPlayerMP playerMP) {
                playerMP.closeScreen();
            }
            return;
        }

        int diameter = this.chunkRadius * 2 - 1;
        if (this.chunkIndex >= diameter * diameter) return;
        if (FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter() % this.scanTick != 0) return;

        World world = player.world;
        int playerChunkX = player.chunkCoordX;
        int playerChunkZ = player.chunkCoordZ;

        int ox = this.chunkIndex % diameter - this.chunkRadius + 1;
        int oz = this.chunkIndex / diameter - this.chunkRadius + 1;

        Chunk chunk = world.getChunk(playerChunkX + ox, playerChunkZ + oz);
        PacketProspecting packet = new PacketProspecting(playerChunkX + ox, playerChunkZ + oz, playerChunkX,
                playerChunkZ, (int) player.posX, (int) player.posZ, this.mode);

        switch (this.mode) {
            case ORE -> scanOres(chunk, packet);
            case FLUID -> scanFluids(world, chunk, packet);
        }

        syncToClient(UPDATE_MAP, packet::writePacketData);
        this.chunkIndex++;
    }

    private static void scanOres(Chunk chunk, PacketProspecting packet) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int ySize = chunk.getHeightValue(x, z);
                for (int y = 1; y < ySize; y++) {
                    pos.setPos(x, y, z);
                    IBlockState state = chunk.getBlockState(pos);
                    ItemStack itemBlock = GTUtility.toItem(state);
                    if (!GTUtility.isOre(itemBlock)) continue;

                    boolean added = false;
                    String oreDictString = OreDictUnifier.getOreDictionaryNames(itemBlock).stream()
                            .findFirst()
                            .orElse("");
                    OrePrefix prefix = OreDictUnifier.getPrefix(itemBlock);
                    if (prefix != null) {
                        for (StoneType type : StoneType.STONE_TYPE_REGISTRY) {
                            if (type.processingPrefix == prefix && type.shouldBeDroppedAsItem) {
                                packet.addBlock(x, y, z, oreDictString);
                                added = true;
                                break;
                            } else if (type.processingPrefix == prefix) {
                                MaterialStack materialStack = OreDictUnifier.getMaterial(itemBlock);
                                if (materialStack != null) {
                                    String oreDict = "ore" + oreDictString.replaceFirst(prefix.name(), "");
                                    packet.addBlock(x, y, z, oreDict);
                                    added = true;
                                    break;
                                }
                            }
                        }
                    }
                    // probably another mod's ore, fall back to whatever ore dict name it has
                    if (!added) {
                        packet.addBlock(x, y, z, oreDictString);
                    }
                }
            }
        }
    }

    private static void scanFluids(World world, Chunk chunk, PacketProspecting packet) {
        BedrockFluidVeinHandler.FluidVeinWorldEntry vein = BedrockFluidVeinHandler
                .getFluidVeinWorldEntry(world, chunk.x, chunk.z);
        if (vein == null || vein.getDefinition() == null) return;

        packet.addBlock(0, 3, 0, TextFormattingUtil.formatNumbers(100.0 *
                BedrockFluidVeinHandler.getOperationsRemaining(world, chunk.x, chunk.z) /
                BedrockFluidVeinHandler.MAXIMUM_VEIN_OPERATIONS));
        packet.addBlock(0, 2, 0,
                String.valueOf(BedrockFluidVeinHandler.getFluidYield(world, chunk.x, chunk.z)));
        Fluid fluid = BedrockFluidVeinHandler.getFluidInChunk(world, chunk.x, chunk.z);
        if (fluid != null) {
            packet.addBlock(0, 1, 0, fluid.getName());
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id != UPDATE_MAP) return;
        PacketProspecting packet = PacketProspecting.readPacketData(buf);
        if (packet != null && this.packetConsumer != null) {
            this.packetConsumer.accept(packet);
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {}
}
