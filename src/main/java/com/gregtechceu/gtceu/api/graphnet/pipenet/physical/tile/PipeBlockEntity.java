package com.gregtechceu.gtceu.api.graphnet.pipenet.physical.tile;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.blockentity.NeighborCacheBlockEntity;
import com.gregtechceu.gtceu.api.capability.IToolable;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.graphnet.logic.NetLogicData;
import com.gregtechceu.gtceu.api.graphnet.logic.NetLogicEntry;
import com.gregtechceu.gtceu.api.graphnet.logic.NetLogicRegistry;
import com.gregtechceu.gtceu.api.graphnet.pipenet.WorldPipeNet;
import com.gregtechceu.gtceu.api.graphnet.pipenet.WorldPipeNetNode;
import com.gregtechceu.gtceu.api.graphnet.pipenet.logic.TemperatureLogic;
import com.gregtechceu.gtceu.api.graphnet.pipenet.physical.IInsulatable;
import com.gregtechceu.gtceu.api.graphnet.pipenet.physical.IPipeCapabilityObject;
import com.gregtechceu.gtceu.api.graphnet.pipenet.physical.IPipeStructure;
import com.gregtechceu.gtceu.api.graphnet.pipenet.physical.block.PipeBlock;

import com.gregtechceu.gtceu.api.item.tool.IToolGridHighLight;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.pipenet.PipeCoverContainer;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;

public class PipeBlockEntity extends NeighborCacheBlockEntity implements IWorldPipeNetTile, ITickSubscription, IEnhancedManaged,
        IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, IToolGridHighLight, IToolable {

    public static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private final Int2ObjectOpenHashMap<NetLogicData> netLogicDatas = new Int2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<NetLogicData.LogicDataListener> listeners = new ObjectOpenHashSet<>();

    // information that is only required for determining graph topology should be stored on the tile entity level,
    // while information interacted with during graph traversal should be stored on the NetLogicData level.

    @Persisted
    @DescSynced
    private byte connectionMask;
    @Persisted
    @DescSynced
    private byte renderMask;
    @Persisted
    @DescSynced
    private byte blockedMask;
    @Persisted
    @DescSynced
    private int paintingColor = -1;

    @Getter
    @Setter
    @DescSynced
    private @Nullable Material frameMaterial;

    private final Set<TickableSubscription> tickers = new ObjectOpenHashSet<>();

    @Persisted
    @DescSynced
    @Getter
    protected final PipeCoverContainer covers = new PipeCoverContainer(this);
    private final Object2ObjectOpenHashMap<Capability<?>, IPipeCapabilityObject> capabilities = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenCustomHashMap<WorldPipeNetNode, PipeCapabilityWrapper> netCapabilities = WorldPipeNet
            .getSensitiveHashMap();

    @Nullable
    private TemperatureLogic temperatureLogic;
    @OnlyIn(Dist.CLIENT)
    @Nullable
    private GTOverheatParticle overheatParticle;

    private final int offset = (int) (Math.random() * 20);

    private long nextDamageTime = 0;
    private long nextSoundTime = 0;

    public PipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);

    }

    @Nullable
    public PipeBlockEntity getPipeNeighbor(Direction facing, boolean allowChunkloading) {
        BlockEntity tile = allowChunkloading ? getNeighbor(facing) : getNeighborNoChunkloading(facing);
        if (tile instanceof PipeBlockEntity pipe) return pipe;
        else return null;
    }

    public void getDrops(@NotNull NonNullList<ItemStack> drops, @NotNull BlockState state) {
        drops.add(getMainDrop(state));
        if (getFrameMaterial() != null)
            drops.add(GTBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, getFrameMaterial()).asStack());
    }

    @Override
    public void validate() {
        super.validate();
        scheduleRenderUpdate();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        // TODO I hate this so much can someone please make it so that covers go through getDrops()?
        getCoverHolder().dropAllCovers();
    }

    public ItemStack getMainDrop(@NotNull BlockState state) {
        return new ItemStack(state.getBlock(), 1);
    }

    public ItemStack getDrop() {
        return new ItemStack(getBlockType(), 1, getBlockType().damageDropped(getBlockState()));
    }

    public long getOffsetTimer() {
        return Platform.getMinecraftServer().getTickCount() + offset;
    }

    public void placedBy(ItemStack stack, Player player) {}

    public IPipeStructure getStructure() {
        return getBlockType().getStructure();
    }

    // mask //

    public boolean canConnectTo(Direction facing) {
        return this.getStructure().canConnectTo(facing, connectionMask);
    }

    public void setConnected(Direction facing, boolean renderClosed) {
        this.connectionMask |= 1 << facing.ordinal();
        updateActiveStatus(facing, false);
        if (renderClosed) {
            this.renderMask |= 1 << facing.ordinal();
        } else {
            this.renderMask &= ~(1 << facing.ordinal());
        }
    }

    public void setDisconnected(Direction facing) {
        this.connectionMask &= ~(1 << facing.ordinal());
        this.renderMask &= ~(1 << facing.ordinal());
        updateActiveStatus(facing, false);
    }

    public boolean isConnected(Direction facing) {
        return (this.connectionMask & 1 << facing.ordinal()) > 0;
    }

    public boolean isConnectedCoverAdjusted(Direction facing) {
        CoverBehavior cover;
        return ((this.connectionMask & 1 << facing.ordinal()) > 0) ||
                (cover = getCoverHolder().getCoverAtSide(facing)) != null && cover.forcePipeRenderConnection();
    }

    public boolean renderClosed(Direction facing) {
        return (this.renderMask & 1 << facing.ordinal()) > 0;
    }

    public byte getConnectionMask() {
        return connectionMask;
    }

    public void setBlocked(Direction facing) {
        this.blockedMask |= (byte) (1 << facing.ordinal());
    }

    public void setUnblocked(Direction facing) {
        this.blockedMask &= (byte) ~(1 << facing.ordinal());
    }

    public boolean isBlocked(Direction facing) {
        return (this.blockedMask & 1 << facing.ordinal()) > 0;
    }

    public byte getBlockedMask() {
        return blockedMask;
    }

    // paint //

    public int getPaintingColor() {
        return isPainted() ? paintingColor : getDefaultPaintingColor();
    }

    public void setPaintingColor(int paintingColor, boolean alphaSensitive) {
        if (!alphaSensitive) {
            paintingColor |= 0xFF000000;
        }
        this.paintingColor = paintingColor;
    }

    public boolean isPainted() {
        return this.paintingColor != -1;
    }

    public int getDefaultPaintingColor() {
        return DEFAULT_COLOR;
    }

    // ticking //

    public void addTicker(TickableSubscription ticker) {
        this.tickers.add(ticker);
    }

    public void update() {
        this.tickers.forEach(TickableSubscription::run);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        initialize();
    }

    public void removeTicker(TickableSubscription ticker) {
        this.tickers.remove(ticker);
    }

    public boolean isTicking() {
        return !tickers.isEmpty();
    }

    // activeness //

    @Override
    public void onNeighborChanged(@NotNull Direction facing) {
        super.onNeighborChanged(facing);
        updateActiveStatus(facing, false);
    }

    /**
     * Returns a map of facings to tile entities that should have at least one of the required capabilities.
     * 
     * @param node the node for this tile entity. Used to identify the capabilities to match.
     * @return a map of facings to tile entities.
     */
    public @NotNull EnumMap<Direction, BlockEntity> getTargetsWithCapabilities(WorldPipeNetNode node) {
        PipeCapabilityWrapper wrapper = netCapabilities.get(node);
        EnumMap<Direction, BlockEntity> caps = new EnumMap<>(Direction.class);
        if (wrapper == null) return caps;

        for (Direction facing : GTUtil.DIRECTIONS) {
            if (wrapper.isActive(facing)) {
                BlockEntity tile = getNeighbor(facing);
                if (tile == null) updateActiveStatus(facing, false);
                else caps.put(facing, tile);
            }
        }
        return caps;
    }

    @Override
    public @Nullable TileEntity getTargetWithCapabilities(WorldPipeNetNode node, Direction facing) {
        PipeCapabilityWrapper wrapper = netCapabilities.get(node);
        if (wrapper == null || !wrapper.isActive(facing)) return null;
        else return getNeighbor(facing);
    }

    @Override
    public PipeCapabilityWrapper getWrapperForNode(WorldPipeNetNode node) {
        return netCapabilities.get(node);
    }

    /**
     * Updates the pipe's active status based on the tile entity connected to the side.
     * 
     * @param facing            the side to check. Can be null, in which case all sides will be checked.
     * @param canOpenConnection whether the pipe is allowed to open a new connection if it finds a tile it can connect
     *                          to.
     */
    public void updateActiveStatus(@Nullable Direction facing, boolean canOpenConnection) {
        if (facing == null) {
            for (Direction side : GTUtil.DIRECTIONS) {
                updateActiveStatus(side, canOpenConnection);
            }
            return;
        }
        if (!this.isConnectedCoverAdjusted(facing) && !(canOpenConnection && canConnectTo(facing))) {
            setAllIdle(facing);
            return;
        }

        BlockEntity tile = getNeighbor(facing);
        if (tile == null || tile instanceof PipeBlockEntity) {
            setAllIdle(facing);
            return;
        }

        boolean oneActive = false;
        for (var netCapability : netCapabilities.entrySet()) {
            for (Capability<?> cap : netCapability.getValue().capabilities) {
                if (tile.getCapability(cap, facing.getOpposite()).isPresent()) {
                    oneActive = true;
                    netCapability.getValue().setActive(facing);
                    break;
                }
            }
        }
        if (canOpenConnection && oneActive) this.setConnected(facing, false);
    }

    private void setAllIdle(Direction facing) {
        for (var netCapability : netCapabilities.entrySet()) {
            netCapability.getValue().setIdle(facing);
        }
    }

    // capability //

    private void addCapabilities(IPipeCapabilityObject[] capabilities) {
        for (IPipeCapabilityObject capabilityObject : capabilities) {
            capabilityObject.setTile(this);
            for (Capability<?> capability : capabilityObject.getCapabilities()) {
                this.capabilities.put(capability, capabilityObject);
            }
        }
    }

    public <T> T getCapabilityCoverQuery(@NotNull Capability<T> capability, @Nullable Direction facing) {
        // covers have access to the capability objects no matter the connection status
        IPipeCapabilityObject object = capabilities.get(capability);
        return object == null ? null : object.getCapabilityForSide(capability, facing);
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> capability, Direction facing) {
        return getCapability(capability, facing) != null;
    }

    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction facing) {
        if (capability == GTCapability.CAPABILITY_COVERABLE) {
            return GTCapability.CAPABILITY_COVERABLE.orEmpty(capability, LazyOptional.of(this::getCovers));
        }
        T pipeCapability;
        IPipeCapabilityObject object = capabilities.get(capability);
        if (object == null || (pipeCapability = object.getCapabilityForSide(capability, facing)) == null)
            pipeCapability = super.getCapability(capability, facing);

        CoverBehavior cover = facing == null ? null : getCoverHolder().getCoverAtSide(facing);
        if (cover == null) {
            if (facing == null || isConnected(facing)) {
                return pipeCapability;
            }
            return null;
        }

        T coverCapability = cover.getCapability(capability, pipeCapability);
        if (coverCapability == pipeCapability) {
            if (isConnectedCoverAdjusted(facing)) {
                return pipeCapability;
            }
            return null;
        }
        return coverCapability;
    }

    // data sync management //

    public NetLogicData getNetLogicData(int networkID) {
        return netLogicDatas.get(networkID);
    }

    @Override
    public @NotNull PipeBlock getBlockType() {
        return (PipeBlock) super.getBlockType();
    }

    @Override
    public void setWorld(@NotNull World worldIn) {
        if (worldIn == this.getWorld()) return;
        super.setWorld(worldIn);
    }

    protected void initialize() {
        if (!getWorld().isRemote) {
            this.netLogicDatas.clear();
            this.capabilities.clear();
            this.netCapabilities.clear();
            this.listeners.forEach(NetLogicData.LogicDataListener::invalidate);
            this.listeners.clear();
            boolean firstNode = true;
            for (WorldPipeNetNode node : PipeBlock.getNodesForTile(this)) {
                this.addCapabilities(node.getNet().getNewCapabilityObjects(node));
                this.netCapabilities.put(node, new PipeCapabilityWrapper(this, node));
                int networkID = node.getNet().getNetworkID();
                netLogicDatas.put(networkID, node.getData());
                var listener = node.getData().createListener(
                        (e, r, f) -> writeCustomData(UPDATE_PIPE_LOGIC, buf -> {
                            buf.writeVarInt(networkID);
                            buf.writeString(e.getName());
                            buf.writeBoolean(r);
                            buf.writeBoolean(f);
                            if (!r) {
                                e.encode(buf, f);
                            }
                        }));
                this.listeners.add(listener);
                node.getData().addListener(listener);
                if (firstNode) {
                    firstNode = false;
                    this.temperatureLogic = node.getData().getLogicEntryNullable(TemperatureLogic.INSTANCE);
                }
                // TODO
                // this and updateActiveStatus() theoretically only need to be called when loading old world data;
                // is there a way to detect that and skip if so?
                node.getNet().updatePredication(node, this);
            }
            this.netLogicDatas.trim();
            this.listeners.trim();
            this.capabilities.trim();
            this.netCapabilities.trim();
            updateActiveStatus(null, false);
        }
    }

    @Override
    public void writeInitialSyncData(@NotNull FriendlyByteBuf buf) {
        buf.writeVarInt(netLogicDatas.size());
        for (var entry : netLogicDatas.entrySet()) {
            buf.writeVarInt(entry.getKey());
            entry.getValue().encode(buf);
        }
    }

    @Override
    public void receiveInitialSyncData(@NotNull FriendlyByteBuf buf) {
        if (level.isClientSide) {
            netLogicDatas.clear();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                int networkID = buf.readVarInt();
                NetLogicData data = new NetLogicData();
                data.decode(buf);
                netLogicDatas.put(networkID, data);
            }
        }
        scheduleRenderUpdate();
    }

    @Override
    public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {
        if (discriminator == UPDATE_PIPE_LOGIC) {
            // extra check just to make sure we don't affect actual net data with our writes
            if (world.isRemote) {
                int networkID = buf.readVarInt();
                String identifier = buf.readString(255);
                boolean removed = buf.readBoolean();
                boolean fullChange = buf.readBoolean();
                if (removed) {
                    this.netLogicDatas.computeIfPresent(networkID, (k, v) -> v.removeLogicEntry(identifier));
                } else {
                    if (fullChange) {
                        NetLogicEntry<?, ?> logic = NetLogicRegistry.getSupplierErroring(identifier).get();
                        logic.decode(buf, true);
                        this.netLogicDatas.compute(networkID, (k, v) -> {
                            if (v == null) v = new NetLogicData();
                            v.setLogicEntry(logic);
                            return v;
                        });
                    } else {
                        NetLogicData data = this.netLogicDatas.get(networkID);
                        if (data != null) {
                            NetLogicEntry<?, ?> entry = data.getLogicEntryNullable(identifier);
                            if (entry != null) entry.decode(buf, false);
                            data.markLogicEntryAsUpdated(entry, false);
                        } else return;
                    }
                    if (identifier.equals(TemperatureLogic.INSTANCE.getName())) {
                        TemperatureLogic tempLogic = this.netLogicDatas.get(networkID)
                                .getLogicEntryNullable(TemperatureLogic.INSTANCE);
                        if (tempLogic != null) updateTemperatureLogic(tempLogic);
                    }
                }
            }
        } else if (discriminator == UPDATE_CONNECTIONS) {
            this.connectionMask = buf.readByte();
            this.renderMask = buf.readByte();
            scheduleRenderUpdate();
        } else if (discriminator == UPDATE_BLOCKED_CONNECTIONS) {
            this.blockedMask = buf.readByte();
            scheduleRenderUpdate();
        } else if (discriminator == UPDATE_FRAME_MATERIAL) {
            String name = buf.readString(255);
            if (name.equals("")) this.frameMaterial = null;
            else this.frameMaterial = GregTechAPI.materialManager.getMaterial(name);
            scheduleRenderUpdate();
        } else if (discriminator == UPDATE_PAINT) {
            this.paintingColor = buf.readInt();
            scheduleRenderUpdate();
        } else {
            this.getCoverHolder().readCustomData(discriminator, buf);
        }
    }

    // particle //

    public void updateTemperatureLogic(@NotNull TemperatureLogic logic) {
        if (overheatParticle == null || !overheatParticle.isAlive()) {
            long tick = FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter();
            int temp = logic.getTemperature(tick);
            if (temp > GTOverheatParticle.TEMPERATURE_CUTOFF) {
                IPipeStructure structure = this.getStructure();
                overheatParticle = new GTOverheatParticle(this, logic, structure.getPipeBoxes(this),
                        structure instanceof IInsulatable i && i.isInsulated());
                GTParticleManager.INSTANCE.addEffect(overheatParticle);
            }
        } else {
            overheatParticle.setTemperatureLogic(logic);
        }
    }

    public @Nullable TemperatureLogic getTemperatureLogic() {
        return temperatureLogic;
    }

    @OnlyIn(Dist.CLIENT)
    public void killOverheatParticle() {
        if (overheatParticle != null) {
            overheatParticle.setExpired();
            overheatParticle = null;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public boolean isOverheatParticleAlive() {
        return overheatParticle != null && overheatParticle.isAlive();
    }

    @Override
    public void spawnParticles(Direction direction, EnumParticleTypes particleType, int particleCount) {
        if (getWorld() instanceof WorldServer server) {
            server.spawnParticle(particleType,
                    getPos().getX() + 0.5,
                    getPos().getY() + 0.5,
                    getPos().getZ() + 0.5,
                    particleCount,
                    direction.getXOffset() * 0.2 + GTValues.RNG.nextDouble() * 0.1,
                    direction.getYOffset() * 0.2 + GTValues.RNG.nextDouble() * 0.1,
                    direction.getZOffset() * 0.2 + GTValues.RNG.nextDouble() * 0.1,
                    0.1);
        }
    }

    // misc overrides //

    @Override
    public World world() {
        return getWorld();
    }

    @Override
    public BlockPos pos() {
        return getPos();
    }

    @Override
    public void notifyBlockUpdate() {
        getWorld().notifyNeighborsOfStateChange(getPos(), getBlockType(), true);
    }

    @SuppressWarnings("ConstantConditions") // yes this CAN actually be null
    @Override
    public void markDirty() {
        if (getWorld() != null && getPos() != null) {
            getWorld().markChunkDirty(getPos(), this);
        }
    }

    @Override
    public void markAsDirty() {
        markDirty();
        // this most notably gets called when the covers of a pipe get updated, aka the edge predicates need syncing.
        for (var node : this.netCapabilities.keySet()) {
            node.getNet().updatePredication(node, this);
        }
    }

    public static @Nullable PipeBlockEntity getTileNoLoading(BlockPos pos, int dimension) {
        World world = DimensionManager.getWorld(dimension);
        if (world == null || !world.isBlockLoaded(pos)) return null;

        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof PipeBlockEntity pipe) return pipe;
        else return null;
    }

    /**
     * Note - the block corresponding to this tile entity must register any new unlisted properties to the default
     * state.
     */
    @OnlyIn(Dist.CLIENT)
    @MustBeInvokedByOverriders
    public IExtendedBlockState getRenderInformation(IExtendedBlockState state) {
        byte frameMask = 0;
        byte connectionMask = this.connectionMask;
        for (Direction facing : GTUtil.DIRECTIONS) {
            Cover cover = getCoverHolder().getCoverAtSide(facing);
            if (cover != null) {
                frameMask |= 1 << facing.ordinal();
                if (cover.forcePipeRenderConnection()) connectionMask |= 1 << facing.ordinal();
            }
        }
        frameMask = (byte) ~frameMask;
        return state.withProperty(AbstractPipeModel.THICKNESS_PROPERTY, this.getStructure().getRenderThickness())
                .withProperty(AbstractPipeModel.CLOSED_MASK_PROPERTY, renderMask)
                .withProperty(AbstractPipeModel.BLOCKED_MASK_PROPERTY, blockedMask)
                .withProperty(AbstractPipeModel.COLOR_PROPERTY, getPaintingColor())
                .withProperty(AbstractPipeModel.FRAME_MATERIAL_PROPERTY, frameMaterial)
                .withProperty(AbstractPipeModel.FRAME_MASK_PROPERTY, frameMask)
                .withProperty(CoverRendererPackage.PROPERTY, getCoverHolder().createPackage());
    }

    public void getCoverBoxes(Consumer<AxisAlignedBB> consumer) {
        for (Direction facing : GTUtil.DIRECTIONS) {
            if (getCoverHolder().hasCover(facing)) {
                consumer.accept(CoverRendererBuilder.PLATE_AABBS.get(facing));
            }
        }
    }

    @Override
    public void dealAreaDamage(int size, Consumer<EntityLivingBase> damageFunction) {
        long timer = getOffsetTimer();
        if (timer >= this.nextDamageTime) {
            List<EntityLivingBase> entities = getWorld().getEntitiesWithinAABB(EntityLivingBase.class,
                    new AxisAlignedBB(getPos()).grow(size));
            entities.forEach(damageFunction);
            this.nextDamageTime = timer + 20;
        }
    }

    public void playLossSound() {
        long timer = getOffsetTimer();
        if (timer >= this.nextSoundTime) {
            getWorld().playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 1.0F);
            this.nextSoundTime = timer + 20;
        }
    }

    public void visuallyExplode() {
        getWorld().createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1.0f + GTValues.RNG.nextFloat(), false);
    }

    public void setNeighborsToFire() {
        for (Direction side : GTUtil.DIRECTIONS) {
            if (!GTValues.RNG.nextBoolean()) continue;
            BlockPos blockPos = getPos().offset(side);
            IBlockState blockState = getWorld().getBlockState(blockPos);
            if (blockState.getBlock().isAir(blockState, getWorld(), blockPos) ||
                    blockState.getBlock().isFlammable(getWorld(), blockPos, side.getOpposite())) {
                getWorld().setBlockState(blockPos, Blocks.FIRE.getDefaultState());
            }
        }
    }
}