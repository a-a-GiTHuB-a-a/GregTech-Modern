package com.gregtechceu.gtceu.integration.ae2.util;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import appeng.api.stacks.GenericStack;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

/**
 * @author GlodBlock
 * @Description A export only slot to hold {@link appeng.api.stacks.GenericStack}
 * @date 2023/4/22-13:42
 */
public abstract class ExportOnlyAESlot implements IConfigurableSlot, INBTSerializable<CompoundTag>, IContentChangeAware {
    protected final static String CONFIG_TAG = "config";
    protected final static String STOCK_TAG = "stock";

    @Getter
    @Setter
    protected Runnable onContentsChanged = () -> {};

    @Getter
    @Setter
    protected GenericStack config;
    @Getter
    @Setter
    protected GenericStack stock;

    public ExportOnlyAESlot(GenericStack config, GenericStack stock) {
        this.config = config;
        this.stock = stock;
    }

    public ExportOnlyAESlot() {
        this(null, null);
    }

    @Nullable
    public GenericStack requestStack() {
        if (this.stock != null && this.stock.amount() <= 0) {
            this.stock = null;
        }
        if (this.config == null || (this.stock != null && !this.config.what().matches(this.stock))) {
            return null;
        }
        if (this.stock == null) {
            return copy(this.config);
        }
        if (this.stock.amount() <= this.config.amount()) {
            return copy(this.config, this.config.amount() - this.stock.amount());
        }
        return null;
    }

    @Nullable
    public GenericStack exceedStack() {
        if (this.stock != null && this.stock.amount() <= 0) {
            this.stock = null;
        }
        if (this.config == null && this.stock != null) {
            return copy(this.stock);
        }
        if (this.config != null && this.stock != null) {
            if (this.config.what().matches(this.stock) && this.config.amount() < this.stock.amount()) {
                return copy(this.stock, this.stock.amount() - this.config.amount());
            }
            if (!this.config.what().matches(this.stock)) {
                return copy(this.stock);
            }
        }
        return null;
    }

    protected abstract void addStack(GenericStack stack);

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (this.config != null) {
            CompoundTag configTag = GenericStack.writeTag(this.config);
            tag.put(CONFIG_TAG, configTag);
        }
        if (this.stock != null) {
            CompoundTag stockTag = GenericStack.writeTag(this.stock);
            tag.put(STOCK_TAG, stockTag);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains(CONFIG_TAG)) {
            this.config = GenericStack.readTag(tag.getCompound(CONFIG_TAG));
        }
        if (tag.contains(STOCK_TAG)) {
            this.stock = GenericStack.readTag(tag.getCompound(STOCK_TAG));
        }
    }

    public static GenericStack copy(GenericStack stack) {
        return new GenericStack(stack.what(), stack.amount());
    }

    public static GenericStack copy(GenericStack stack, long amount) {
        return new GenericStack(stack.what(), amount);
    }
}
