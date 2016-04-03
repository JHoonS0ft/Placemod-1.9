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

    public static ArrayList<ArrayList<Structure>> clusters = new ArrayList<ArrayList<Structure>>();
    public static ArrayList<String> names = new ArrayList<String>();
    public static ArrayList<Double> chances = new ArrayList<Double>();
    private static double density = 0.005; // drop probability per chunk
    public static boolean[] soil = new boolean[256];
    public static boolean[] overlook = new boolean[256];
    public static boolean[] liquid = new boolean[256];

    /* Load/Generate mod settings */
    public static void configure(File file) {
        new File(file.getParent()).mkdirs();
        Properties config = new Properties();
        if (file.exists()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                config.load(fis);
                density = Double.parseDouble(config.getProperty("DENSITY", Double.toString(density)));
                fis.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            try {
                FileOutputStream fos = new FileOutputStream(file);
                config.setProperty("DENSITY", Double.toString(density));
                config.store(fos, null);
                fos.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    private static void loadStructures(File folder) {
        long startTime = System.currentTimeMillis();
        System.out.print("[PLACEMOD] LOADING SCHEMATICS FROM " + folder.getPath() + "\n");
        Stack<File> folders = new Stack<File>();
        folders.add(folder);
        HashMap<String, ArrayList<Structure>> villages = new HashMap<String, ArrayList<Structure>>();
        long totalBlocks = 0;
        int totalLoaded = 0;
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
                        int width = structure.flags.getShort("Width");
                        int height = structure.flags.getShort("Height");
                        int length = structure.flags.getShort("Length");
                        totalBlocks += width * height * length;
                        totalLoaded++;
                        if (structure.flags.getString("Method").equalsIgnoreCase("Village")) {
                            if (villages.containsKey(file.getParent())) {
                                villages.get(file.getParent()).add(structure);
                            } else {
                                villages.put(file.getParent(), new ArrayList<Structure>() {{
                                    add(structure);
                                }});
                            }
                        } else {
                            clusters.add(new ArrayList<Structure>() {{
                                add(structure);
                            }});
                        }
                        String info =
                            "[PLACEMOD]" +
                            " LOAD " + file.getPath() +
                            "; SIZE = [W=" + width + ";H=" + height + ";L=" + length + "]" +
                            "; LIFT = " + structure.flags.getInteger("Lift") +
                            "; METHOD = " + structure.flags.getString("Method") +
                            "; BIOME = " + Biome.Style.valueOf(structure.flags.getInteger("Biome")).name;
                        System.out.print(info + "\n");
                    } catch (IOException ioe) {
                        String info =
                            "[PLACEMOD]" +
                            " CAN'T LOAD SCHEMATIC " + file.getPath() +
                            "; ERROR = " + ioe.getMessage();
                        System.out.print(info + "\n");
                    }
                } else if (file.isDirectory()) {
                    folders.add(file);
                }
            }
        }
        for (ArrayList<Structure> structure : villages.values()) {
            clusters.add(structure);
        }
        double averageBlocks = totalBlocks / clusters.size();
        double chancesSum = 0;
        for (ArrayList<Structure> cluster : clusters) {
            long weight = 0;
            for (Structure structure : cluster) {
                int width = structure.flags.getShort("Width");
                int height = structure.flags.getShort("Height");
                int length = structure.flags.getShort("Length");
                weight += width * height * length;
            }
            // f(x) = 2/(1+e^(-x^0.5))-1
            double saturation = 2.0 / (1.0 + Math.exp(-Math.pow(weight / averageBlocks, 0.5))) - 1.0;
            double chance = 1.0 - saturation;
            chances.add(chance);
            chancesSum += chance;
        }
        for (int i = 0; i < chances.size(); ++i) {
            chances.set(i, chances.get(i) / chancesSum);
        }
        long loadTime = (System.currentTimeMillis() - startTime);
        String info =
            "[PLACEMOD]" +
            "  SUCCESSFULLY LOADED CLUSTERS " + clusters.size() +
            "; SCHEMATICS = " + totalLoaded +
            "; LOAD TIME = " + new DecimalFormat("###0.00").format(loadTime / 1000.0) + "s";
        System.out.print(info + "\n");
    }

    @Override
    public void generate(Random randomDefault, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        Random random = getRandom(world.getSeed(), chunkX, chunkZ);
        int drops = (int) density + (random.nextDouble() <= (density - (int) density) ? 1 : 0);
        for (int i = 0; i < drops; ++i) {
            double pointer = random.nextDouble();
            for (int j = 0; j < chances.size(); ++j) {
                if (pointer <= chances.get(j)) {
                    BiomeGenBase biome = world.getBiomeGenForCoords(new BlockPos(chunkX * 16, 64, chunkZ * 16));
                    for (Structure structure : clusters.get(j)) {
                        if (Biome.compare(biome, Biome.Style.valueOf(structure.flags.getInteger("Biome")))) {
                            place(world, clusters.get(j), chunkX, chunkZ, random.nextLong());
                            break;
                        }
                    }
                    break;
                }
                pointer -= chances.get(j);
            }
        }
    }

    public static Random getRandom(long seed, int chunkX, int chunkZ) {
        long chunkIndex = (long)chunkX << 32 | chunkZ & 0xFFFFFFFFL;
        Random random = new Random(chunkIndex ^ seed);
        for (int i = 0; i < 16; ++i) {
            random.nextDouble();
        }
        return random;
    }

    public void place(World world, ArrayList<Structure> cluster, int chunkX, int chunkZ, long seed) {
        Random random = new Random(seed);
        int structureBound = 1;
        for (Structure structure : cluster) {
            int width = structure.flags.getShort("Width");
            int length = structure.flags.getShort("Length");
            structureBound = Math.max(structureBound, Math.max(width, length));
        }
        int clusterRoad = -2 + structureBound / 8;
        int dir = 0;
        int nx = 0, nz = 0;
        int cx = chunkX * 16 + Math.abs(random.nextInt()) % 16;
        int cz = chunkZ * 16 + Math.abs(random.nextInt()) % 16;
        for (Structure structure : cluster) {
            Posture posture = new Posture(0, 0, 0,
                    0, random.nextInt() % 4, 0,
                    random.nextBoolean(), false, random.nextBoolean(),
                    structure.flags.getShort("Width"),
                    structure.flags.getShort("Height"),
                    structure.flags.getShort("Length"));
            int sx = cx + nx * (clusterRoad + structureBound) - (posture.getPosX() / 2);
            int sz = cz + nz * (clusterRoad + structureBound) - (posture.getPosZ() / 2);
            posture.shift(sx, 0, sz);
            try {
                structure.paste(world, posture, random.nextLong());
                nz += dir == 0 ? -1 : (dir == 2 ? 1 : 0);
                nx += dir == 1 ? 1 : (dir == 3 ? -1 : 0);
                dir = (dir == 0 ? (nx == nz + 1) : (Math.abs(nx) == Math.abs(nz))) ? (dir + 1) % 4 : dir;
            } catch (IOException iae) {
                System.out.print("CAN'T PLACE STRUCTURE: " + iae.getMessage() + "\n");
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
