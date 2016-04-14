package com.ternsip.placemod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Created by TrnMain on 26.03.2016.
 */

@Mod(   modid = Placemod.MODID,
        name = Placemod.MODNAME,
        version = Placemod.VERSION,
        acceptableRemoteVersions = "*")
public class Placemod {

    static final String MODID = "placemod";
    static final String MODNAME = "Placemod";
    static final String VERSION = "2.3";
    public static final String AUTHOR = "Ternsip";
    public static final String MCVERSION = "1.9.*";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        GameRegistry.registerWorldGenerator(new Decorator(), 4096);
    }

}
