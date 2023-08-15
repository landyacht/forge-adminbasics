package me.roguerakudev.adminbasics;

import net.minecraft.command.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.*;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("adminbasics")
public class AdminBasics
{
    public static final String MOD_ID = "adminbasics";

    private static final Logger LOGGER = LogManager.getLogger();

    public AdminBasics() {
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        // some preinit code
    }

    @SubscribeEvent
    public void doClientStuff(final FMLClientSetupEvent event) {
        throw new UnsupportedOperationException("AdminBasics is a server-only mod");
    }

    @SubscribeEvent
    public void onServerStart(final FMLServerStartingEvent event) {
        LOGGER.warn("AB SERVER START HANDLER");
        event.getCommandDispatcher().register(Commands.literal("ab")
                .then(RegionProtection.getCommands())
                //.then(OtherStuff.getCommands())
        );
    }

    private void enqueueIMC(final InterModEnqueueEvent event)
    {
        // some example code to dispatch IMC to another mod
        //InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
    }

    private void processIMC(final InterModProcessEvent event)
    {
        // some example code to receive and process InterModComms from other mods
        /*LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m->m.getMessageSupplier().get()).
                collect(Collectors.toList()));*/
    }
}
