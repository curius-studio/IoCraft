package com.curius.iocraft.blocks.emisor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** BlockEntity del Bloque de Datos: persiste "nombre", "contenido", "contenidoOff" y "modo". */
public class BloqueEmisorEntity extends BlockEntity {

    /** Modo de envío del comando: solo al encender o también al apagar. */
    public enum ModoEnvio { SOLO_ENCENDIDO, ENCENDIDO_Y_APAGADO }

    private String nombre = "";
    private String contenido = "";      // Param2 cuando hay señal (ON)
    private String contenidoOff = "";   // Param2 cuando se quita la señal (OFF)
    private ModoEnvio modo = ModoEnvio.SOLO_ENCENDIDO;

    private boolean ultimoPowered = false;

    public BloqueEmisorEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntitiesEmisor.EMISOR_BE.get(), pos, state);
    }

    // --------------------
    // Getters / Setters
    // --------------------
    public String getNombre()            { return nombre; }
    public String getContenido()         { return contenido; }
    public String getContenidoOff()      { return contenidoOff; }
    public ModoEnvio getModo()           { return modo; }
    public boolean getUltimoPowered()    { return ultimoPowered; }

    public void setNombre(String n) {
        this.nombre = n != null ? n : "";
        markAndDispatch();
    }

    public void setContenido(String c) {
        this.contenido = c != null ? c : "";
        markAndDispatch();
    }

    public void setContenidoOff(String c) {
        this.contenidoOff = c != null ? c : "";
        markAndDispatch();
    }

    public void setModo(ModoEnvio m) {
        this.modo = (m != null ? m : ModoEnvio.SOLO_ENCENDIDO);
        markAndDispatch();
    }

    public void setUltimoPowered(boolean v) {
        this.ultimoPowered = v;
        setChanged();
    }

    /** Útil si quieres aplicar todo de una vez (p. ej. al guardar en la GUI). */
    public void apply(String n, String on, String off, ModoEnvio m) {
        this.nombre       = n   != null ? n   : "";
        this.contenido    = on  != null ? on  : "";
        this.contenidoOff = off != null ? off : "";
        this.modo         = (m != null ? m : ModoEnvio.SOLO_ENCENDIDO);
        markAndDispatch();
    }

    // --------------------
    // Persistencia (NBT)
    // --------------------
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Nombre", nombre);
        tag.putString("Contenido", contenido);
        tag.putString("ContenidoOff", contenidoOff);
        tag.putString("ModoEnvio", modo.name());
        tag.putBoolean("UltimoPowered", ultimoPowered);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.nombre         = tag.getString("Nombre");
        this.contenido      = tag.getString("Contenido");
        this.contenidoOff   = tag.getString("ContenidoOff");
        try { this.modo     = ModoEnvio.valueOf(tag.getString("ModoEnvio")); } catch (Exception ignored) {}
        this.ultimoPowered  = tag.getBoolean("UltimoPowered");
    }

    // --------------------
    // Sync cliente-servidor
    // --------------------
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
        // Notifica al cliente para que la GUI vea los cambios sin reabrir
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}
