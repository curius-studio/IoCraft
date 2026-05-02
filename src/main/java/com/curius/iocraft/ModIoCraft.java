package com.curius.iocraft;

import com.curius.iocraft.blocks.emisor.ModBlockEntitiesEmisor;
import com.curius.iocraft.blocks.emisor.net.EmisorNetwork;
import com.curius.iocraft.blocks.sensor.SensorRegistry;
import com.curius.iocraft.blocks.sensor.net.SensorNetwork;
import com.curius.iocraft.blocks.computer.ModBlockEntitiesComputer;
import com.curius.iocraft.blocks.computer.net.ComputerNetwork;
import com.curius.iocraft.blocks.puerta.ModBlockEntitiesPuerta;
import com.curius.iocraft.blocks.puerta.net.PuertaNetwork;
import com.curius.iocraft.api.IoCraftApiProvider;
import com.curius.iocraft.api.internal.IoCraftApiImpl;
import com.curius.iocraft.comandos.net.ComandosNetwork;
import com.curius.iocraft.mensajeria.RegistroManejadores;
import com.curius.iocraft.net.CoreNetwork;
import com.curius.iocraft.registro.RegistroContenido;
import com.curius.iocraft.security.AddonPolicyManager;
import com.curius.iocraft.security.BlacklistManager;

import net.minecraftforge.fml.loading.FMLPaths;

import com.curius.iocraft.security.AuthManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

@Mod(ModIoCraft.MOD_ID)
public class ModIoCraft {
    public static final String MOD_ID = "iocraft";
    private static final Logger LOGGER = LogManager.getLogger();

    public ModIoCraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Registries existentes
        SensorRegistry.BE_REG.register(modBus);

        // Delegar bloques/items al registro centralizado
        RegistroContenido.registrar(modBus);

        ModBlockEntitiesComputer.BLOCK_ENTITIES.register(modBus);
        ModBlockEntitiesEmisor.BLOCK_ENTITIES.register(modBus);

        ModBlockEntitiesPuerta.BLOCK_ENTITIES.register(modBus);

        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {

            Path cfg = FMLPaths.CONFIGDIR.get().resolve("iocraft_secrets.json");
            Path cfgBlacklist = FMLPaths.CONFIGDIR.get().resolve("iocraft_blacklist.json");
            Path cfgAddonPolicies = FMLPaths.CONFIGDIR.get().resolve("iocraft_addon_policies.json");

            com.curius.iocraft.security.AuthManager.initPersistence(cfg);
            BlacklistManager.initPersistence(cfgBlacklist);
            AddonPolicyManager.initPersistence(cfgAddonPolicies);

            // Inicializa canal de seguridad
            com.curius.iocraft.security.net.SecurityNetwork.init();

            // (Opcional) dejar activada la autenticación
            com.curius.iocraft.security.AuthManager.REQUIRE_AUTH = true;

            SensorNetwork.init();
            EmisorNetwork.init();
            event.enqueueWork(ComputerNetwork::init);
            ComandosNetwork.init();
            RegistroManejadores.registrarPorDefecto();
            PuertaNetwork.init();
            CoreNetwork.init();

            // API pública mínima (Fase 1): wrapper estable sobre internals.
            IoCraftApiProvider.register(new IoCraftApiImpl());
            LOGGER.info("[IoCraft API] version={} (major={}, minor={}, patch={})",
                    IoCraftApiProvider.apiVersion(),
                    IoCraftApiProvider.apiMajor(),
                    IoCraftApiProvider.apiMinor(),
                    IoCraftApiProvider.apiPatch());
        });
    }
}
