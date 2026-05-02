package com.curius.iocraft.ui;

import com.curius.iocraft.registro.NombresContenido;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.TextComponent;

import java.util.List;

public class ListaDispositivos extends ObjectSelectionList<ListaDispositivos.Entrada> {
    private Entrada seleccionado;
    MenuDispositivos pantallaPadre; // package-private para usar desde la entrada

    public ListaDispositivos(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
    }

    public void setDispositivos(List<MenuDispositivos.Dispositivo> dispositivos, MenuDispositivos padre) {
        this.clearEntries();
        this.pantallaPadre = padre;
        for (MenuDispositivos.Dispositivo d : dispositivos) {
            this.addEntry(new Entrada(d, this));
        }
        // reinicia selección
        this.seleccionado = null;
        if (pantallaPadre != null) pantallaPadre.actualizarEstadoBotonConectar();
    }

    @Override
    protected int getScrollbarPosition() {
        return this.width / 2 + 200 - 6;
    }

    @Override
    public int getRowWidth() {
        return 400;
    }

    void setSeleccionado(Entrada entrada) {
        this.seleccionado = entrada;
        if (pantallaPadre != null) {
            pantallaPadre.actualizarEstadoBotonConectar();
        }
    }

    public MenuDispositivos.Dispositivo getSeleccionado() {
        return seleccionado != null ? seleccionado.disp : null;
    }

    public static class Entrada extends ObjectSelectionList.Entry<Entrada> {
        private final MenuDispositivos.Dispositivo disp;
        private final ListaDispositivos lista;

        // coords del mini botón "X"
        private int btnX, btnY, btnW = 12, btnH = 12;

        public Entrada(MenuDispositivos.Dispositivo disp, ListaDispositivos lista) {
            this.disp = disp;
            this.lista = lista;
        }

        @Override
        public void render(PoseStack pose, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {

            // Fondo de selección
            if (lista.getSeleccionado() == disp) {
                fill(pose, x, y, x + entryWidth, y + entryHeight, 0x80FFFFFF);
            }

            Minecraft mc = Minecraft.getInstance();

            // Texto: nombre, IP y estado
            mc.font.draw(pose, disp.nombre, x + 10, y + 6, 0xFFFFFF);
            mc.font.draw(pose, disp.ip, x + 220, y + 6, 0xA0A0A0);
            int col = disp.disponible ? 0x80FF80 : 0xFF8080;
            mc.font.draw(pose, disp.disponible ? NombresContenido.UI_INICIAL.BOTON_DISPONIBLE : NombresContenido.UI_INICIAL.BOTON_NoDISPONIBLE,
                    x + 320, y + 6, col);

            // Mini-botón "X" (rojo) a la derecha
            btnW = 12; btnH = 12;
            btnX = x + entryWidth - btnW - 6;
            btnY = y + (entryHeight - btnH) / 2;

            // Fondo del botón
            int bg = 0xFFAA5555; // rojo
            fill(pose, btnX, btnY, btnX + btnW, btnY + btnH, bg);

            // Borde simple
            int border = 0xFF772F2F;
            fill(pose, btnX, btnY, btnX + btnW, btnY + 1, border);
            fill(pose, btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, border);
            fill(pose, btnX, btnY, btnX + 1, btnY + btnH, border);
            fill(pose, btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, border);

            // Letra "X"
            mc.font.draw(pose, "X", btnX + 3, btnY + 2, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Recalcular la caja de la "X" en este instante:
            int idx = lista.children().indexOf(this);
            int x = lista.getRowLeft();
            int y = lista.getRowTop(idx);
            int entryWidth = lista.getRowWidth();
            int entryHeight = lista.itemHeight; // field de ObjectSelectionList

            int btnSize = 14; // un poco más grande para acertar mejor
            int btnX = x + entryWidth - btnSize - 6;
            int btnY = y + (entryHeight - btnSize) / 2;

            boolean clickEnX = mouseX >= btnX && mouseX <= btnX + btnSize
                    && mouseY >= btnY && mouseY <= btnY + btnSize;

            if (clickEnX) {
                if (lista.pantallaPadre != null) {
                    lista.pantallaPadre.expulsarDispositivo(disp);
                }
                return true; // consumimos el clic, NO selecciona la fila
            }

            // Si no fue sobre la X, entonces sí selecciona la fila.
            lista.setSeleccionado(this);
            return true;
        }


        @Override
        public TextComponent getNarration() {
            return new TextComponent(disp.nombre + " - " + disp.ip +
                    (disp.disponible ? " (" + NombresContenido.UI_INICIAL.BOTON_DISPONIBLE +")" : " (" + NombresContenido.UI_INICIAL.BOTON_NoDISPONIBLE + ")"));
        }
    }
}
