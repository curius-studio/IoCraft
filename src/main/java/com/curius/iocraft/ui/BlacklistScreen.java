package com.curius.iocraft.ui;

import com.curius.iocraft.security.BlacklistManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import java.util.ArrayList;
import java.util.List;

public class BlacklistScreen extends Screen {
    private final Screen parent;
    private EntryList list;
    private Button btnDesbloquear;

    private static final class EntryData {
        final boolean device;
        final String value;

        EntryData(boolean device, String value) {
            this.device = device;
            this.value = value;
        }
    }

    public BlacklistScreen(Screen parent) {
        super(new TextComponent("Bloqueados"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int top = 36;
        int bottom = this.height - 56;
        this.list = new EntryList(this.minecraft, this.width, this.height, top, bottom, 22);
        this.addRenderableWidget(this.list);

        int y = this.height - 28;
        this.btnDesbloquear = this.addRenderableWidget(new Button(this.width / 2 - 140, y, 130, 20,
                new TextComponent("Desbloquear"),
                b -> desbloquearSeleccionado()));
        this.btnDesbloquear.active = false;

        this.addRenderableWidget(new Button(this.width / 2 + 10, y, 130, 20,
                new TextComponent("Volver"),
                b -> this.onClose()));

        recargar();
    }

    private void recargar() {
        List<EntryData> rows = new ArrayList<>();
        for (String d : BlacklistManager.snapshotDevices()) rows.add(new EntryData(true, d));
        for (String ip : BlacklistManager.snapshotIps()) rows.add(new EntryData(false, ip));
        this.list.setRows(rows);
        this.btnDesbloquear.active = this.list.getSelectedData() != null;
    }

    private void desbloquearSeleccionado() {
        EntryData s = this.list.getSelectedData();
        if (s == null) return;
        if (s.device) BlacklistManager.removeDevice(s.value);
        else BlacklistManager.removeIp(s.value);
        recargar();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);
        this.list.render(pose, mouseX, mouseY, partialTick);
        super.render(pose, mouseX, mouseY, partialTick);
        drawCenteredString(pose, this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        drawCenteredString(pose, this.font, "Tipo | Valor", this.width / 2, 24, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private final class EntryList extends ObjectSelectionList<BlacklistEntry> {
        private final List<EntryData> rows = new ArrayList<>();

        EntryList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
        }

        void setRows(List<EntryData> data) {
            this.rows.clear();
            this.rows.addAll(data);
            this.clearEntries();
            for (EntryData d : this.rows) this.addEntry(new BlacklistEntry(d));
            this.setSelected(null);
        }

        EntryData getSelectedData() {
            BlacklistEntry s = this.getSelected();
            return s == null ? null : s.data;
        }

        @Override
        public int getRowWidth() {
            return 420;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 210;
        }
    }

    private final class BlacklistEntry extends ObjectSelectionList.Entry<BlacklistEntry> {
        private final EntryData data;

        private BlacklistEntry(EntryData data) {
            this.data = data;
        }

        @Override
        public void render(PoseStack pose, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (list.getSelected() == this) {
                fill(pose, x, y, x + entryWidth, y + entryHeight, 0x60FFFFFF);
            }
            String tipo = data.device ? "DEVICE" : "IP";
            font.draw(pose, tipo, x + 8, y + 7, data.device ? 0xFFDD88 : 0x88CCFF);
            font.draw(pose, data.value, x + 90, y + 7, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            list.setSelected(this);
            btnDesbloquear.active = true;
            return true;
        }

        @Override
        public TextComponent getNarration() {
            return new TextComponent((data.device ? "device " : "ip ") + data.value);
        }
    }
}
