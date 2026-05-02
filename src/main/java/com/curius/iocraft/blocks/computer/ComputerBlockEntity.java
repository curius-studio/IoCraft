package com.curius.iocraft.blocks.computer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** BE para el “Computer Chat”: persiste destino y un buffer de hasta 20 mensajes. */
public class ComputerBlockEntity extends BlockEntity {

    private String destino = "";
    private int revision = 0;
    private static final int MAX_LOG = 20;
    private final Deque<String> mensajes = new ArrayDeque<>(MAX_LOG);

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntitiesComputer.COMPUTER_BE.get(), pos, state);
    }

    // --------- API simple ---------
    public String getDestino() { return destino; }
    public void setDestino(String d) { this.destino = d != null ? d : ""; markAndDispatch(); }

    // Ya existente
    public synchronized void pushMessage(String line) {
        if (mensajes.size() >= MAX_LOG) mensajes.removeFirst();
        mensajes.addLast(line != null ? line : "");
        revision++;
        markAndDispatch();
    }

    /** Copia para la GUI. */
    public List<String> getMessagesSnapshot() {
        return new ArrayList<>(mensajes);
    }

    public synchronized int getRevision() {
        return revision;
    }

    // NUEVA sobrecarga correcta
    public void pushMessage(String author, String text) {
        pushMessage((author != null && !author.isEmpty() ? author + ": " : "") + (text != null ? text : ""));
    }





    // --------- Persistencia NBT ---------
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Destino", destino);

        // 👇 añade esto
        tag.putInt("Revision", revision);

        tag.putInt("MsgCount", mensajes.size());
        int i = 0;
        for (String s : mensajes) {
            tag.putString("Msg" + i, s);
            i++;
        }
    }


    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.destino = tag.getString("Destino");

        // 👇 añade esto (si no existe aún, queda 0)
        this.revision = tag.contains("Revision") ? tag.getInt("Revision") : this.revision;

        mensajes.clear();
        int count = tag.getInt("MsgCount");
        for (int i = 0; i < count; i++) {
            if (mensajes.size() >= MAX_LOG) break;
            mensajes.addLast(tag.getString("Msg" + i));
        }
    }


    // --------- Sync cliente-servidor ---------
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) load(tag);
    }

    private void markAndDispatch() {
        if (level == null) return;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}
