package com.curius.iocraft.blocks.sensor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SensorBlockEntity extends BlockEntity {

    private String nombre = "";
    public enum Modo { COINCIDENCIA, UMBRAL, RANGO }
    private Modo modo = Modo.COINCIDENCIA;
    private String valorActivador = "";
    public enum Operador { LT, LE, EQ, GE, GT }
    private Operador operador = Operador.LE;
    private double umbral = 0.0;
    private double min = 0.0, max = 0.0;

    // estado lógico -> POWERED del bloque
    private boolean activo = false;

    public SensorBlockEntity(BlockPos pos, BlockState state) {
        super(SensorRegistry.RECEPTOR_BE.get(), pos, state);
    }

    // Getters/Setters con sync
    public String getNombre() { return nombre; }
    public void setNombre(String n) { this.nombre = n; markAndDispatch(); }

    public Modo getModo() { return modo; }
    public void setModo(Modo m) { this.modo = m; markAndDispatch(); }

    public String getValorActivador() { return valorActivador; }
    public void setValorActivador(String v) { this.valorActivador = v; markAndDispatch(); }

    public Operador getOperador() { return operador; }
    public void setOperador(Operador op) { this.operador = op; markAndDispatch(); }

    public double getUmbral() { return umbral; }
    public void setUmbral(double u) { this.umbral = u; markAndDispatch(); }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public void setRango(double min, double max) { this.min = min; this.max = max; markAndDispatch(); }

    public boolean isActivo() { return activo; }
    private void setActivo(boolean v) {
        if (this.activo == v) return;
        this.activo = v;
        if (level != null) {
            BlockState st = getBlockState();
            if (st.hasProperty(SensorBlock.POWERED)) {
                BlockState ns = st.setValue(SensorBlock.POWERED, v);
                level.setBlock(worldPosition, ns, 3);
                level.updateNeighborsAt(worldPosition, ns.getBlock());
            }
            setChanged();
        }
    }

    // Persistencia
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Nombre", nombre);
        tag.putString("Modo", modo.name());
        tag.putString("MatchValue", valorActivador);
        tag.putString("Operador", operador.name());
        tag.putDouble("Umbral", umbral);
        tag.putDouble("Min", min);
        tag.putDouble("Max", max);
        tag.putBoolean("Activo", activo);
        tag.putInt("DataVersion", 1);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.nombre = tag.getString("Nombre");
        try { this.modo = Modo.valueOf(tag.getString("Modo")); } catch (Exception ignored) {}
        this.valorActivador = tag.getString("MatchValue");
        try { this.operador = Operador.valueOf(tag.getString("Operador")); } catch (Exception ignored) {}
        this.umbral = tag.getDouble("Umbral");
        this.min = tag.getDouble("Min");
        this.max = tag.getDouble("Max");
        this.activo = tag.contains("Activo") && tag.getBoolean("Activo");
    }

    @Override public CompoundTag getUpdateTag() { CompoundTag t = new CompoundTag(); saveAdditional(t); return t; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag(); if (tag != null) load(tag);
    }

    private void markAndDispatch() {
        if (level == null) return;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void applyConfig(String nombre, Modo modo, String match, Operador op, double umbral, double min, double max) {
        this.nombre = nombre != null ? nombre : "";
        this.modo = modo != null ? modo : Modo.COINCIDENCIA;
        this.valorActivador = match != null ? match : "";
        this.operador = op != null ? op : Operador.LE;
        this.umbral = umbral;
        this.min = min;
        this.max = max;
        markAndDispatch();
    }

    // === Hook llamado desde PacketSensorData.handle (lado servidor) ===
    public void onIncomingFromNetwork(String type, String data, String device) {
        boolean result = evaluate(type, data);
        setActivo(result);
    }

    private boolean evaluate(String type, String raw) {
        if (raw == null) raw = "";
        switch (modo) {
            case COINCIDENCIA:
                return !valorActivador.isEmpty() && raw.equalsIgnoreCase(valorActivador);

            case UMBRAL: {
                Double v = tryParseDouble(raw);
                if (v == null) return false;
                return switch (operador) {
                    case LT -> v <  umbral;
                    case LE -> v <= umbral;
                    case EQ -> Double.compare(v, umbral) == 0;
                    case GE -> v >= umbral;
                    case GT -> v >  umbral;
                };
            }

            case RANGO: {
                Double v = tryParseDouble(raw);
                if (v == null) return false;
                return v >= min && v <= max;
            }
        }
        return false;
    }

    private static Double tryParseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return null; }
    }
}
