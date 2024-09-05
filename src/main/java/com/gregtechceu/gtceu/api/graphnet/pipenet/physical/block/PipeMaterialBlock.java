package com.gregtechceu.gtceu.api.graphnet.pipenet.physical.block;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.graphnet.pipenet.IPipeNetNodeHandler;
import com.gregtechceu.gtceu.api.graphnet.pipenet.physical.IPipeMaterialStructure;
import com.gregtechceu.gtceu.api.graphnet.pipenet.physical.tile.MaterialPipeBlockEntity;
import com.gregtechceu.gtceu.api.graphnet.pipenet.physical.tile.PipeBlockEntity;
import com.gregtechceu.gtceu.common.data.GTBlockEntities;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

public abstract class PipeMaterialBlock extends PipeBlock {

    public final Material material;

    public PipeMaterialBlock(BlockBehaviour.Properties properties, IPipeMaterialStructure structure,
                             Material material) {
        super(properties, structure);
        this.material = material;
    }

    @Nullable
    public static PipeMaterialBlock getBlockFromItem(@NotNull ItemStack stack) {
        if (stack.getItem() instanceof MaterialPipeBlockItem block) return block.getBlock();
        else return null;
    }

    @Override
    public IPipeMaterialStructure getStructure() {
        return (IPipeMaterialStructure) super.getStructure();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaterialPipeBlockEntity(GTBlockEntities.MATERIAL_PIPE.get(), pos, state);
    }

    @Override
    protected @NotNull IPipeNetNodeHandler getHandler(BlockGetter world, BlockPos pos) {
        return material.getProperty(PropertyKey.PIPENET_PROPERTIES);
    }

    @Override
    protected @NotNull IPipeNetNodeHandler getHandler(@NotNull ItemStack stack) {
        return material.getProperty(PropertyKey.PIPENET_PROPERTIES);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (ConfigHolder.INSTANCE.dev.debug) {
            if (material != null)
                tooltip.add(Component
                        .literal("MetaItem Id: " + getStructure().getPrefix().name + material.toCamelCaseString()));
        }
    }

    // tile entity //

    @Override
    public @Nullable MaterialPipeBlockEntity getBlockEntity(@NotNull BlockGetter world, @NotNull BlockPos pos) {
        if (lastTilePos.get().equals(pos)) {
            PipeBlockEntity tile = lastTile.get().get();
            if (tile != null && !tile.isRemoved()) return (MaterialPipeBlockEntity) tile;
        }
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof MaterialPipeBlockEntity pipe) {
            lastTilePos.set(pos.immutable());
            lastTile.set(new WeakReference<>(pipe));
            return pipe;
        } else return null;
    }
}