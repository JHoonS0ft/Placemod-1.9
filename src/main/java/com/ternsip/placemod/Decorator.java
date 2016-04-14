package com.ternsip.placemod;


import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

/* This class statically loads blueprints and mod configuration and override generate method @author Ternsip */
public class Decorator implements IWorldGenerator {

    private static Distributor distributor = null;
    private static double density = 0.005; // drop probability per chunk
    static double ratioA = 1, ratioB = 0.5;
    static boolean[] soil = new boolean[256];
    static boolean[] overlook = new boolean[256];
    static boolean[] liquid = new boolean[256];

    /* Load/Generate mod settings */
    private static void configure(File file) {
        new File(file.getParent()).mkdirs();
        Properties config = new Properties();
        if (file.exists()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                config.load(fis);
                density = Double.parseDouble(config.getProperty("DENSITY", Double.toString(density)));
                ratioA = Double.parseDouble(config.getProperty("RATIO_A", Double.toString(ratioA)));
                ratioB = Double.parseDouble(config.getProperty("RATIO_B", Double.toString(ratioB)));
                fis.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        try {
            FileOutputStream fos = new FileOutputStream(file);
            config.setProperty("DENSITY", Double.toString(density));
            config.setProperty("RATIO_A", Double.toString(ratioA));
            config.setProperty("RATIO_B", Double.toString(ratioB));
            config.store(fos, null);
            fos.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static void loadStructures(File folder) {
        long startTime = System.currentTimeMillis();
        new Report().add("LOADING SCHEMATICS FROM", folder.getPath()).print();
        Stack<File> folders = new Stack<File>();
        folders.add(folder);
        ArrayList<Cluster> clusters = new ArrayList<Cluster>();
        HashMap<String, Cluster> villages = new HashMap<String, Cluster>();
        int loaded = 0;
        while (!folders.empty()) {
            File dir = folders.pop();
            File[] listOfFiles = dir.listFiles();
            for (File file : listOfFiles != null ? listOfFiles : new File[0]) {
                if (file.isFile()) {
                    try {
                        String pathParallel = file.getPath().replace("\\", "/").replace("//", "/").replace("/Schematics/", "/Structures/");
                        String pathFlags = pathParallel.replace(".schematic", ".flags");
                        String pathStructure = pathParallel.replace(".schematic", ".structure");
                        final Structure structure = new Structure(file, new File(pathFlags), new File(pathStructure));
                        loaded++;
                        String parent = file.getParent();
                        if (structure.flags.getString("Method").equalsIgnoreCase("Village")) {
                            if (villages.containsKey(parent)) {
                                villages.get(parent).add(structure);
                            } else {
                                villages.put(parent, new Cluster(parent).add(structure));
                            }
                        } else {
                            clusters.add(new Cluster(parent).add(structure));
                        }
                        int width = structure.flags.getShort("Width");
                        int height = structure.flags.getShort("Height");
                        int length = structure.flags.getShort("Length");
                        new Report()
                                .add("LOAD", file.getPath())
                                .add("SIZE", "[W=" + width + ";H=" + height + ";L=" + length + "]")
                                .add("LIFT", String.valueOf(structure.flags.getInteger("Lift")))
                                .add("METHOD", structure.flags.getString("Method"))
                                .add("BIOME", Biome.Style.valueOf(structure.flags.getInteger("Biome")).name)
                                .print();
                    } catch (IOException ioe) {
                        new Report()
                                .add("CAN'T LOAD SCHEMATIC", file.getPath())
                                .add("ERROR", ioe.getMessage())
                                .print();
                    }
                } else if (file.isDirectory()) {
                    folders.add(file);
                }
            }
        }
        clusters.addAll(villages.values());
        distributor = new Distributor(clusters);
        long loadTime = (System.currentTimeMillis() - startTime);
        new Report()
                .add("LOADED CLUSTERS", String.valueOf(clusters.size()))
                .add("LOADED SCHEMATICS", String.valueOf(loaded))
                .add("LOAD TIME", new DecimalFormat("###0.00").format(loadTime / 1000.0) + "s")
                .print();
    }


    @Override
    public void generate(Random randomDefault, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        Random random = getRandom(world.getSeed(), chunkX, chunkZ);
        int drops = (int) density + (random.nextDouble() <= (density - (int) density) ? 1 : 0);
        BiomeGenBase biome = world.getBiomeGenForCoords(new BlockPos(chunkX * 16, 64, chunkZ * 16));
        Biome.Style biomeStyle = Biome.determine(biome);
        ArrayList<Cluster> biomeClusters = distributor.getClusters(biomeStyle);
        for (int i = 0; i < drops; ++i) {
            double pointer = random.nextDouble();
            for (Cluster cluster : biomeClusters) {
                if (pointer <= cluster.getChance()) {
                    place(world, cluster, chunkX, chunkZ, random.nextLong());
                    break;
                }
                pointer -= cluster.getChance();
            }
        }
    }

    private static Random getRandom(long seed, int chunkX, int chunkZ) {
        long chunkIndex = (long)chunkX << 32 | chunkZ & 0xFFFFFFFFL;
        Random random = new Random(chunkIndex ^ seed);
        for (int i = 0; i < 16; ++i) {
            random.nextDouble();
        }
        return random;
    }

    private void place(World world, Cluster cluster, int chunkX, int chunkZ, long seed) {
        Random random = new Random(seed);
        int cx = chunkX * 16 + Math.abs(random.nextInt()) % 16;
        int cz = chunkZ * 16 + Math.abs(random.nextInt()) % 16;
        new Report()
                .add("PLACE CLUSTER", cluster.getName())
                .add("POS", "[CHUNK_X=" + chunkX + ";CHUNK_Z=" + chunkZ + "]")
                .add("SIZE", String.valueOf(cluster.getStructures().size()))
                .print();
        int curX = cx, curZ = cz, maxZ = 0;
        int timer = 0, delay = (int) Math.ceil(Math.sqrt(cluster.getStructures().size()));
        ArrayList<Structure> structures = new ArrayList<Structure>(cluster.getStructures());
        Collections.shuffle(structures, random);
        for (Structure structure : structures) {
            int rotX = 0, rotY = random.nextInt() % 4, rotZ = 0;
            boolean flipX = random.nextBoolean(), flipY = false, flipZ = random.nextBoolean();
            int width = structure.flags.getShort("Width");
            int height = structure.flags.getShort("Height");
            int length = structure.flags.getShort("Length");
            Posture posture = new Posture(0, 0, 0, rotX, rotY, rotZ, flipX, flipY, flipZ, width, height, length);
            if (--timer <= 0) {
                timer = delay;
                curX = cx;
                curZ += maxZ;
                maxZ = 0;
            }
            int sx = curX;
            int sz = curZ;
            curX += posture.getSizeX() + 1;
            maxZ = Math.max(maxZ, posture.getSizeZ());
            posture.shift(sx, 0, sz);
            try {
                long startTime = System.currentTimeMillis();
                posture.shift(0, structure.calibrate(world, posture, seed), 0);
                structure.paste(world, posture, random.nextLong());
                long spawnTime = System.currentTimeMillis() - startTime;
                new Report()
                        .add("PASTED", structure.schematicFile.getPath())
                        .add("SPAWN TIME", new DecimalFormat("###0.00").format(spawnTime / 1000.0) + "s")
                        .add("POS", "[X=" + posture.getPosX() + ";Y=" + posture.getPosY() + ";Z=" + posture.getPosZ() + "]")
                        .add("SIZE", "[W=" + width + ";H=" + height + ";L=" + length + "]")
                        .add("BIOME", Biome.Style.valueOf(structure.flags.getInteger("Biome")).name)
                        .add("ROTATE", "[X=" + posture.getRotateX() + ";Y=" + posture.getRotateY() + ";Z=" + posture.getRotateZ() + "]")
                        .add("FLIP", "[X=" + posture.isFlipX() + ";Y=" + posture.isFlipY() + ";Z=" + posture.isFlipZ() + "]")
                        .print();
            } catch (IOException ioe) {
                new Report().add("CAN'T PASTE", structure.schematicFile.getPath())
                        .add("ERROR",  ioe.getMessage())
                        .add("POS", "[X=" + posture.getPosX() + ";Y=" + posture.getPosY() + ";Z=" + posture.getPosZ() + "]")
                        .add("SIZE", "[W=" + width + ";H=" + height + ";L=" + length + "]")
                        .add("BIOME", Biome.Style.valueOf(structure.flags.getInteger("Biome")).name)
                        .add("ROTATE", "[X=" + posture.getRotateX() + ";Y=" + posture.getRotateY() + ";Z=" + posture.getRotateZ() + "]")
                        .add("FLIP", "[X=" + posture.isFlipX() + ";Y=" + posture.isFlipY() + ";Z=" + posture.isFlipZ() + "]")
                        .print();
            }
        }
    }

    static {

        configure(new File("config/placemod.cfg"));

        soil[Block.getIdFromBlock(Blocks.grass)] = true;
        soil[Block.getIdFromBlock(Blocks.dirt)] = true;
        soil[Block.getIdFromBlock(Blocks.stone)] = true;
        soil[Block.getIdFromBlock(Blocks.cobblestone)] = true;
        soil[Block.getIdFromBlock(Blocks.sandstone)] = true;
        soil[Block.getIdFromBlock(Blocks.netherrack)] = true;
        soil[Block.getIdFromBlock(Blocks.gravel)] = true;
        soil[Block.getIdFromBlock(Blocks.sand)] = true;

        overlook[Block.getIdFromBlock(Blocks.air)] = true;
        overlook[Block.getIdFromBlock(Blocks.log)] = true;
        overlook[Block.getIdFromBlock(Blocks.log2)] = true;
        overlook[Block.getIdFromBlock(Blocks.leaves)] = true;
        overlook[Block.getIdFromBlock(Blocks.leaves2)] = true;
        overlook[Block.getIdFromBlock(Blocks.sapling)] = true;
        overlook[Block.getIdFromBlock(Blocks.web)] = true;
        overlook[Block.getIdFromBlock(Blocks.tallgrass)] = true;
        overlook[Block.getIdFromBlock(Blocks.deadbush)] = true;
        overlook[Block.getIdFromBlock(Blocks.yellow_flower)] = true;
        overlook[Block.getIdFromBlock(Blocks.red_flower)] = true;
        overlook[Block.getIdFromBlock(Blocks.red_mushroom_block)] = true;
        overlook[Block.getIdFromBlock(Blocks.brown_mushroom_block)] = true;
        overlook[Block.getIdFromBlock(Blocks.brown_mushroom)] = true;
        overlook[Block.getIdFromBlock(Blocks.fire)] = true;
        overlook[Block.getIdFromBlock(Blocks.wheat)] = true;
        overlook[Block.getIdFromBlock(Blocks.snow_layer)] = true;
        overlook[Block.getIdFromBlock(Blocks.snow)] = true;
        overlook[Block.getIdFromBlock(Blocks.cactus)] = true;
        overlook[Block.getIdFromBlock(Blocks.pumpkin)] = true;
        overlook[Block.getIdFromBlock(Blocks.vine)] = true;
        overlook[Block.getIdFromBlock(Blocks.waterlily)] = true;
        overlook[Block.getIdFromBlock(Blocks.double_plant)] = true;

        liquid[Block.getIdFromBlock(Blocks.water)] = true;
        liquid[Block.getIdFromBlock(Blocks.flowing_water)] = true;
        liquid[Block.getIdFromBlock(Blocks.ice)] = true;
        liquid[Block.getIdFromBlock(Blocks.lava)] = true;
        liquid[Block.getIdFromBlock(Blocks.flowing_lava)] = true;

        loadStructures(new File("Placemod/Schematics/"));

    }

}
