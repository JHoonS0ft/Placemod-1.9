package com.ternsip.placemod;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.util.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class Structure {

    public NBTTagCompound flags = new NBTTagCompound();
    public File schematicFile = null;
    public File flagFile = null;
    public File structureFile = null;


    public Structure(File schematicFile, File flagFile, File structureFile) throws IOException {
        /* Load structure if it exists */
        this.schematicFile = schematicFile;
        this.flagFile = flagFile;
        this.structureFile = structureFile;
        if (flagFile.exists() && structureFile.exists()) {
            FileInputStream fis = new FileInputStream(flagFile);
            flags = CompressedStreamTools.readCompressed(fis);
            fis.close();
            return;
        }
        /* Load schematic */
        FileInputStream fis = new FileInputStream(schematicFile);
        NBTTagCompound schematic = CompressedStreamTools.readCompressed(fis);
        fis.close();
        String materials =  schematic.getString("Materials");
        if (!materials.equals("Alpha")) throw new IOException("Schematic is not an Alpha material");
        int width = schematic.getShort("Width");
        int height = schematic.getShort("Height");
        int length = schematic.getShort("Length");
        if (width == 0 || height == 0 || length == 0) throw new IOException("Zero size schematic");
        byte[] addBlocks = schematic.getByteArray("AddBlocks");
        byte[] blocksID = schematic.getByteArray("Blocks");
        if (width * height * length != blocksID.length) throw new IOException("Wrong schematic size");
        short[] blocks = compose(blocksID, addBlocks);
        /* Set flags */
        String path = schematicFile.getPath().toLowerCase().replace("\\", "/").replace("//", "/");
        flags.setString("Method", "Common");
        if (path.contains("/underground/")) flags.setString("Method", "Underground");
        if (path.contains("/village/")) flags.setString("Method", "Village");
        if (path.contains("/floating/")) flags.setString("Method", "Floating");
        if (path.contains("/water/")) flags.setString("Method", "Water");
        if (path.contains("/underwater/")) flags.setString("Method", "Underwater");
        flags.setInteger("Biome", Biome.detect(blocks).value);
        if (flags.getString("Method").equalsIgnoreCase("Water") ||
                flags.getString("Method").equalsIgnoreCase("Underwater")) {
            flags.setInteger("Biome", Biome.Style.WATER.value);
        }
        if (flags.getString("Method").equalsIgnoreCase("Floating")) {
            flags.setInteger("Biome", Biome.Style.NORMAL.value);
        }
        flags.setShort("Width", (short) width);
        flags.setShort("Height", (short) height);
        flags.setShort("Length", (short) length);
        flags.setInteger("Lift", getLift(blocks));
        /* Generate structure over schematic */
        schematic.merge(flags);
        schematic.setByteArray("Skin", getSkin(blocks).toByteArray());
        /* Save flags */
        flagFile.getParentFile().mkdirs();
        FileOutputStream fosFlag = new FileOutputStream(flagFile);
        CompressedStreamTools.writeCompressed(flags, fosFlag);
        fosFlag.close();
        /* Save structure */
        structureFile.getParentFile().mkdirs();
        FileOutputStream fosStruct = new FileOutputStream(structureFile);
        CompressedStreamTools.writeCompressed(schematic, fosStruct);
        fosStruct.close();
    }

    public boolean paste(World world, Posture posture, long seed) throws IOException {
        long startTime = System.currentTimeMillis();
        int bestY = calibrate(world, posture, seed);
        long calibrateTime = System.currentTimeMillis() - startTime;
        if (bestY < 4 || bestY > 250) {
            return false;
        }
        posture.shift(0, bestY, 0);
        {
            String info = "[PLACEMOD]" +
                    " START PASTE " + schematicFile.getName() +
                    "; SIZE = [W=" + posture.getWidth() + ";H=" + posture.getHeight() + ";L=" + posture.getLength() + "]" +
                    "; METHOD = " + flags.getString("Method") +
                    "; POS = [X=" + posture.getPosX() + ";Y=" + posture.getPosY() + ";Z=" + posture.getPosZ() + "]" +
                    "; ROTATE = [X=" + posture.getRotateX() + ";Y=" + posture.getRotateY() + ";Z=" + posture.getRotateZ() + "]" +
                    "; FLIP = [X=" + posture.isFlipX() + ";Y=" + posture.isFlipY() + ";Z=" + posture.isFlipZ() + "]" +
                    "; BIOME = " + Biome.Style.valueOf(flags.getInteger("Biome")).name +
                    "; CALIBRATE TIME = " + new DecimalFormat("###0.00").format(calibrateTime / 1000.0) + "s";
            System.out.print(info + "\n");
        }
        /* Load ad paste structure */
        NBTTagCompound structure;
        FileInputStream fis = new FileInputStream(structureFile);
        structure = CompressedStreamTools.readCompressed(fis);
        fis.close();
        NBTTagList entities = structure.getTagList("TileEntities", Constants.NBT.TAG_COMPOUND);
        byte[] blocksMetadata = structure.getByteArray("Data");
        final byte[] addBlocks = structure.getByteArray("AddBlocks");
        byte[] blocksID = structure.getByteArray("Blocks");
        short[] blocks = compose(blocksID, addBlocks);
        BitSet skin = BitSet.valueOf(structure.getByteArray("Skin"));
        /* Prepare tiles */
        Random random = new Random();
        ArrayList<ResourceLocation> lootTables = new ArrayList<ResourceLocation>() {{
            add(LootTableList.CHESTS_ABANDONED_MINESHAFT);
            add(LootTableList.CHESTS_JUNGLE_TEMPLE);
            add(LootTableList.CHESTS_SIMPLE_DUNGEON);
            add(LootTableList.CHESTS_SPAWN_BONUS_CHEST);
            add(LootTableList.CHESTS_STRONGHOLD_CORRIDOR);
            add(LootTableList.CHESTS_STRONGHOLD_CROSSING);
            add(LootTableList.CHESTS_STRONGHOLD_LIBRARY);
        }};
        if (flags.getString("Method").equalsIgnoreCase("Village")) {
            lootTables.add(LootTableList.CHESTS_VILLAGE_BLACKSMITH);
        }
        if (flags.getString("Biome").equalsIgnoreCase("Sand")) {
            lootTables.add(LootTableList.CHESTS_DESERT_PYRAMID);
        }
        if (flags.getString("Biome").equalsIgnoreCase("Snow")) {
            lootTables.add(LootTableList.CHESTS_IGLOO_CHEST);
        }
        if (flags.getString("Biome").equalsIgnoreCase("Nether")) {
            lootTables.add(LootTableList.CHESTS_NETHER_BRIDGE);
        }
        if (flags.getString("Biome").equalsIgnoreCase("End")) {
            lootTables.add(LootTableList.CHESTS_END_CITY_TREASURE);
        }
        long loadTime = System.currentTimeMillis() - startTime - calibrateTime;
        /* Paste */
        int width = posture.getWidth();
        int height = posture.getHeight();
        int length = posture.getLength();
        int bedrock = Block.getIdFromBlock(Blocks.bedrock);
        int startChunkX = posture.getStartChunkX();
        int startChunkZ = posture.getStartChunkZ();
        int sizeChunkX = posture.getEndChunkX() - startChunkX + 1;
        int sizeChunkZ = posture.getEndChunkZ() - startChunkZ + 1;
        ExtendedBlockStorage[][][] storage = new ExtendedBlockStorage[sizeChunkX][sizeChunkZ][16];
        for (int cx = 0; cx < sizeChunkX; ++cx) {
            for (int cz = 0; cz < sizeChunkZ; ++cz) {
                Chunk chunk = world.getChunkFromChunkCoords(cx + startChunkX, cz + startChunkZ);
                for (int sy = 0; sy < 256; sy += 16) {
                    IBlockState state = chunk.getBlockState(0, sy, 0);
                    chunk.setBlockState(new BlockPos(0, sy, 0), Blocks.log.getDefaultState());
                    chunk.setBlockState(new BlockPos(0, sy, 0), state);
                }
                ExtendedBlockStorage[] stack = chunk.getBlockStorageArray();
                System.arraycopy(stack, 0, storage[cx][cz], 0, 16);
            }
        }
        for (int y = 0, index = 0; y < height; ++y) {
            for (int z = 0; z < length; ++z) {
                for (int x = 0; x < width ; ++x, ++index) {
                    if (skin.get(index)) {
                        continue;
                    }
                    BlockPos blockPos = posture.getWorldPos(x, y, z);
                    if (blockPos.getY() < 0 || blockPos.getY() > 255 || bedrock == blocks[index]) {
                        continue;
                    }
                    Block block = Block.getBlockById(blocks[index]);
                    int meta = posture.getWorldMeta(block, blocksMetadata[index]);
                    IBlockState state = block.getStateFromMeta(meta);
                    int rx = blockPos.getX() - startChunkX * 16;
                    int ry = blockPos.getY();
                    int rz = blockPos.getZ() - startChunkZ * 16;
                    storage[rx / 16][rz / 16][ry / 16].set(rx % 16, ry % 16, rz % 16, state);
                    //world.setBlockState(blockPos, state);
                    //chunk.setModified(true);
                    //world.setBlockState(blockPos, state, 2);
                    TileEntity blockTile = world.getTileEntity(blockPos);
                    if (blockTile != null && blockTile instanceof TileEntityChest) {
                        TileEntityChest chest = (TileEntityChest) blockTile;
                        int id = Math.abs(random.nextInt() % lootTables.size());
                        chest.setLoot(lootTables.get(id), random.nextLong());
                    }
                }
            }
        }
        /* Populate */
        if (flags.getString("Method").equalsIgnoreCase("Village")) {
            int count = (int) (1 + Math.cbrt(Math.abs(posture.getWidth() * posture.getLength()))) / 2;
            int maxTries = 16 + count * count;
            for (int i = 0; i < maxTries && count > 0; ++i) {
                int xPos = posture.getPosX() + random.nextInt() % posture.getSizeX();
                int yPos = posture.getPosY() + random.nextInt() % posture.getSizeY();
                int zPos = posture.getPosZ() + random.nextInt() % posture.getSizeZ();
                if (!world.isAirBlock(new BlockPos(xPos, yPos, zPos)) || !world.isAirBlock(new BlockPos(xPos, yPos + 1, zPos))) {
                    continue;
                }
                EntityVillager villager = new EntityVillager(world, random.nextInt() % 5);
                float facing = MathHelper.wrapAngleTo180_float(random.nextFloat() * 360.0F);
                villager.setLocationAndAngles(xPos + 0.5, yPos + 0.1, zPos + 0.5, facing, 0.0F);
                world.spawnEntityInWorld(villager);
                villager.playLivingSound();
                --count;
            }
        }
        /* Spawn entities */
        long spawnTime = System.currentTimeMillis() - startTime - calibrateTime - loadTime;
        {
            String info = "[PLACEMOD]" +
                    " END PASTE " + schematicFile.getPath() +
                    "; LOAD TIME = " + new DecimalFormat("###0.00").format(loadTime / 1000.0) + "s" +
                    "; SPAWN TIME = " + new DecimalFormat("###0.00").format(spawnTime / 1000.0) + "s";
            System.out.print(info + "\n");
        }
        return true;
    }

    /* Calibrates posture, returns false if can't calibrate */
    private int calibrate(World world, Posture posture, long seed) {
        Random random = new Random(seed);
        double totalHeight = 0;
        double squareHeightSum = 0;
        double totalHeightWater = 0;
        double squareHeightSumWater = 0;
        boolean[] overlook = Decorator.overlook;
        boolean[] liquid = Decorator.liquid;
        int ex = posture.getEndX();
        int ez = posture.getEndZ();
        for (int wx = posture.getPosX(); wx < ex; ++wx) {
            for (int wz = posture.getPosZ(); wz < ez; ++wz) {
                int hg = world.getHeight(new BlockPos(wx, 0, wz)).getY();
                hg = hg == 0 ? 128 : hg;
                while (hg > 0) {
                    int blockID = Block.getIdFromBlock(world.getBlockState(new BlockPos(wx, hg, wz)).getBlock());
                    if (blockID >= 0 && blockID < 256 && overlook[blockID]) {
                        --hg;
                    } else {
                        break;
                    }
                }
                totalHeight += hg + 1;
                squareHeightSum += (hg + 1) * (hg + 1);
                while (hg > 0) {
                    int blockID = Block.getIdFromBlock(world.getBlockState(new BlockPos(wx, hg, wz)).getBlock());
                    if (blockID >= 0 && blockID < 256 && (overlook[blockID] || liquid[blockID])) {
                        --hg;
                    } else {
                        break;
                    }
                }
                totalHeightWater += hg + 1;
                squareHeightSumWater += (hg + 1) * (hg + 1);
            }
        }
        int width = flags.getShort("Width");
        int height = flags.getShort("Height");
        int length = flags.getShort("Length");
        double area = width * length;
        double averageHeight = totalHeight / area;
        double variance = Math.abs((squareHeightSum - (totalHeight * totalHeight) / area) / (area - 1));
        double averageHeightWater = totalHeightWater / area;
        double varianceWater = Math.abs((squareHeightSumWater - (totalHeightWater * totalHeightWater) / area) / (area - 1));
        double waterHeight = averageHeight - averageHeightWater;
        double lift = flags.getInteger("Lift");
        boolean water = flags.getString("Method").equalsIgnoreCase("Water");
        boolean underwater = flags.getString("Method").equalsIgnoreCase("Underwater");
        boolean floating = flags.getString("Method").equalsIgnoreCase("Floating");
        boolean underground = flags.getString("Method").equalsIgnoreCase("Underground");
        int sy;
        if (water) {
            if (Math.sqrt(variance) > 3.0 || waterHeight < 6.0) {
                return -1;
            }
            sy = (int) (averageHeight - Math.sqrt(variance));
        } else {
            if (underwater) {
                if ((Math.sqrt(varianceWater) > height / 6.0 + 2) || waterHeight < height * 0.75) {
                    return -1;
                }
            } else if (!floating && !underground) {
                if ((Math.sqrt(varianceWater) > height / 8.0 + 2) || waterHeight > 1.5) {
                    return -1;
                }
            }
            sy = (int) (averageHeightWater - Math.sqrt(varianceWater));
        }
        if (floating) {
            sy += 8 + height + random.nextInt() % (height / 2);
        } else if (underground) {
            sy = 30 + random.nextInt() % 25;
        } else {
            sy -= lift;
        }
        return sy;
    }


    private short[] compose(byte[] blocksID, byte[] addBlocks) {
        short[] blocks = new short[blocksID.length];
        for (int index = 0; index < blocksID.length; index++) {
            if ((index >> 1) >= addBlocks.length) {
                blocks[index] = (short) (blocksID[index] & 0xFF);
            } else {
                if ((index & 1) == 0) {
                    blocks[index] = (short) (((addBlocks[index >> 1] & 0x0F) << 8) + (blocksID[index] & 0xFF));
                } else {
                    blocks[index] = (short) (((addBlocks[index >> 1] & 0xF0) << 4) + (blocksID[index] & 0xFF));
                }
            }
        }
        return blocks;
    }

    private int getLift(short[] blocks) {
        int width = flags.getShort("Width");
        int height = flags.getShort("Height");
        int length = flags.getShort("Length");
        int[][] level = new int[width][length];
        int[][] levelMax = new int[width][length];
        boolean[] liquid = Decorator.liquid;
        boolean[] soil = Decorator.soil;
        Posture posture = new Posture(0, 0, 0, 0, 0, 0, false, false, false, width, height, length);
        for (int index = 0; index < blocks.length; ++index) {
            if (blocks[index] >= 0 && blocks[index] < 256) {
                if (soil[blocks[index]] || liquid[blocks[index]]) {
                    level[posture.getX(index)][posture.getZ(index)] += 1;
                    levelMax[posture.getX(index)][posture.getZ(index)] = posture.getY(index) + 1;
                }
            }
        }
        long borders = 0, totals = 0;
        for (int x = 0; x < width; ++x) {
            for (int z = 0; z < length; ++z) {
                totals += level[x][z];
                if (x == 0 || z == 0 || x == width - 1 || z == length - 1) {
                    borders += levelMax[x][z];
                }
            }
        }
        int borderLevel = (int) Math.round(borders / ((width + length) * 2.0));
        int wholeLevel = Math.round(totals / (width * length));
        return  Math.max(borderLevel, wholeLevel);
    }

    private BitSet getSkin(short[] blocks) {
        // Directions 0 - not clipped, 1 - y -> 0, 2 - x -> 0, 4 - x -> max, 8 - z => 0, 16 - z => max
        int width = flags.getShort("Width");
        int height = flags.getShort("Height");
        int length = flags.getShort("Length");
        Posture posture = new Posture(0, 0, 0, 0, 0, 0, false, false, false, width, height, length);
        HashSet<Integer> skinBlocks = new HashSet<Integer>();
        skinBlocks.add(Block.getIdFromBlock(Blocks.air));
        if (    flags.getString("Method").equalsIgnoreCase("Water") ||
                flags.getString("Method").equalsIgnoreCase("Underwater")) {
            skinBlocks.add(Block.getIdFromBlock(Blocks.water));
            skinBlocks.add(Block.getIdFromBlock(Blocks.flowing_water));
        }
        Queue<Integer> indexQueue = new LinkedList<Integer>();
        byte[] clipped = new byte[width * height * length];
        BitSet working = new BitSet(width * height * length);
        BitSet skin = new BitSet(width * height * length);
        for (int dir = 0; dir <= 1; ++dir) {
            for (int y = 0; y < height; ++y) {
                for (int z = 0; z < length; ++z) {
                    int index = dir == 0 ? posture.getIndex(0, y, z) : posture.getIndex(width - 1, y, z);
                    int flag = dir == 0 ? 2 : 4;
                    if (skinBlocks.contains((int) blocks[index])) {
                        if (!working.get(index)) {
                            indexQueue.add(index);
                            working.set(index);
                        }
                        clipped[index] |= flag;
                        skin.set(index);
                    }
                }
                for (int x = 0; x < width; ++x) {
                    int index = dir == 0 ? posture.getIndex(x, y, 0) : posture.getIndex(x, y, length - 1);
                    int flag = dir == 0 ? 8 : 16;
                    if (skinBlocks.contains((int) blocks[index])) {
                        if (!working.get(index)) {
                            indexQueue.add(index);
                            working.set(index);
                        }
                        clipped[index] |= flag;
                        skin.set(index);
                    }
                }
            }
        }
        int[] flagsID = {1, 1, 2, 4, 4, 2, 8, 16, 16, 8};
        while (!indexQueue.isEmpty()) {
            int index = indexQueue.remove();
            working.clear(index);
            int x = posture.getX(index);
            int y = posture.getY(index);
            int z = posture.getZ(index);
            int[] idx = {
                    posture.getIndex(x, y - 1, z),
                    posture.getIndex(x + 1, y, z),
                    posture.getIndex(x - 1, y, z),
                    posture.getIndex(x, y, z + 1),
                    posture.getIndex(x, y, z - 1)};
            boolean[] cond = {y > 0, x < width - 1, x > 0, z < length - 1, z > 0};
            for (int k = 0, fi = 0; k <= 4; ++k, fi += 2) {
                if (cond[k] &&
                        (clipped[index] & flagsID[fi]) > 0 &&
                        (clipped[idx[k]] & flagsID[fi]) == 0 &&
                        (clipped[idx[k]] & flagsID[fi + 1]) == 0 &&
                        (skinBlocks.contains((int) blocks[idx[k]]))) {
                    if (!working.get(idx[k])) {
                        working.set(idx[k]);
                        indexQueue.add(idx[k]);
                    }
                    clipped[idx[k]] |= flagsID[fi];
                    skin.set(idx[k]);
                }
            }
        }
        return skin;
    }


}
