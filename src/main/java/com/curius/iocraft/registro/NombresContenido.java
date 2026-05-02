package com.curius.iocraft.registro;

import com.curius.iocraft.ModIoCraft;
import net.minecraft.resources.ResourceLocation;

public final class NombresContenido {
    private NombresContenido() {}

    /** Helper para crear ResourceLocation del mod. */
    public static ResourceLocation rl(String path) {
        return new ResourceLocation(ModIoCraft.MOD_ID, path);
    }

    public static final String COMPUTADORA_BE_V = "computer_be";
    public static final String EMISOR_BE_V = "emisor_be";
    public static final String RECEPTOR_BE_V = "receptor_be";

    /** IDs de bloques (coinciden con assets: blockstates, models, etc.) */
    public static final class Bloques {
        public static final String COMPUTADORA = "computer";
        public static final String EMISOR      = "emisor";
        public static final String RECEPTOR    = "receptor";
        public static final String PUERTA      = "puerta_iocraft";
        private Bloques() {}
    }

    /** IDs de items (coinciden con assets: models/item, textures/item, etc.) */
    public static final class Items {
        public static final String ICONO_TAB   = "icono_tab";
        public static final String COMPUTADORA = Bloques.COMPUTADORA; // BlockItem
        public static final String EMISOR      = Bloques.EMISOR;      // BlockItem
        public static final String RECEPTOR    = Bloques.RECEPTOR;    // BlockItem
        public static final String PUERTA      = Bloques.PUERTA;
        private Items() {}
    }

    /** IDs de botones e interfaces  */
    public static final class UI_INICIAL {
        public static final int DESPLAZAMIENTO_LINEAS_BOTON = -48;
        public static final String BOTON_MENU            = "Dispositivos";
        public static final String BOTON_DISPONIBLE      = "Disponible";
        public static final String BOTON_NoDISPONIBLE    = "No disponible";
        public static final String BOTON_ACTUALIZAR              = "Actualizar";
        public static final String BOTON_UNICAST              = "Probar conexión";
        public static final String BOTON_BROADCAST              = "Probar conexiones";
        public static final String BOTON_INFO            = "Info de conexión";
        public static final String BOTON_CERRAR              = "Cerrar";
        public static final String MSG_UNICAST              = "Hola desde Minecraft";
        public static final String MSG_BROADCAST              = "Hola a todos desde Minecraft";
        public static final String TITULO_MENU            = "Dispositivos";
        public static final String BOTON_COPIARURL            = "Copiar URL";
        public static final String LABEL_SubtituloHOST            = "Host: ";
        public static final String LABEL_SubtituloPUERTO            = "Puerto: ";
        public static final String LABEL_SubtituloURL            = "URL: ";
        public static final String LABEL_NOTA1            = "Nota: El servidor escucha en todas las interfaces. Para que otros dispositivos se conecten, ";
        public static final String LABEL_NOTA2            = "usa tu IP de la red local mostrada arriba (por ejemplo 192.168.x.x o 10.x.x.x).";
        private UI_INICIAL() {}
    }

    /** Claves de traducción útiles si las necesitas desde código. */
    public static String claveBloque(String id) { return "block." + ModIoCraft.MOD_ID + "." + id; }
    public static String claveItem (String id) { return "item."  + ModIoCraft.MOD_ID + "." + id; }
    public static String claveGrupo()           { return "itemGroup." + ModIoCraft.MOD_ID; }
}
