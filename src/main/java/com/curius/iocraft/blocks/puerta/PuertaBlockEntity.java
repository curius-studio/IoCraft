package com.curius.iocraft.blocks.puerta;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;



public class PuertaBlockEntity extends BlockEntity {

    private String destino = "";
    private String mensajeAbrir = "";
    private String mensajeCerrar = "";

    public PuertaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntitiesPuerta.TIPO_PUERTA.get(), pos, state);
    }

    public String getDestino()       { return destino == null ? "" : destino; }
    public void setDestino(String d) { this.destino = d; setChangedAndSync(); }

    public String getMensajeAbrir()  { return mensajeAbrir == null ? "" : mensajeAbrir; }
    public void setMensajeAbrir(String s) { this.mensajeAbrir = s; setChangedAndSync(); }

    public String getMensajeCerrar() { return mensajeCerrar == null ? "" : mensajeCerrar; }
    public void setMensajeCerrar(String s) { this.mensajeCerrar = s; setChangedAndSync(); }

    public void setConfig(String destino, String msgAbrir, String msgCerrar) {
        this.destino       = destino != null ? destino : "";
        this.mensajeAbrir  = msgAbrir != null ? msgAbrir : "";
        this.mensajeCerrar = msgCerrar != null ? msgCerrar : "";
        setChangedAndSync();
    }


    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.destino       = tag.getString("destino");
        this.mensajeAbrir  = tag.getString("msgAbrir");
        this.mensajeCerrar = tag.getString("msgCerrar");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("destino",   this.destino);
        tag.putString("msgAbrir",  this.mensajeAbrir);
        tag.putString("msgCerrar", this.mensajeCerrar);
    }

    // === Sync vanilla de BlockEntity ===
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        handleUpdateTag(pkt.getTag());
    }

    public void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            var st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, 3);
        }
    }
}
