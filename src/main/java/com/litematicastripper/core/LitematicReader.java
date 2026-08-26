package com.litematicastripper.core;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.*;

public class LitematicReader {

    private final Map<String, Region> regions = new LinkedHashMap<>();
    private int version;
    private int minecraftDataVersion;
    private NbtCompound metadata;

    public static class Region {
        public String name;
        public int posX, posY, posZ;
        public int sizeX, sizeY, sizeZ;
        public List<PaletteEntry> palette = new ArrayList<>();
        public int[] blockIndices;
        public NbtList entities;
        public NbtList tileEntities;

        public int getVolume() {
            return Math.abs(sizeX * sizeY * sizeZ);
        }
    }

    public static class PaletteEntry {
        public String name;
        public NbtCompound properties;

        public PaletteEntry(String name, NbtCompound properties) {
            this.name = name;
            this.properties = properties;
        }
    }

    public void load(File file) throws IOException {
        byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
        NbtCompound root;
        if (data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            try (var bais = new ByteArrayInputStream(data)) {
                root = NbtIo.readCompressed(bais, new NbtSizeTracker(Long.MAX_VALUE, 512));
            }
        } else {
            root = NbtIo.read(file.toPath());
        }
        parse(root);
    }

    private void parse(NbtCompound root) {
        version = root.contains("Version") ? root.getInt("Version") : 5;
        minecraftDataVersion = root.contains("MinecraftDataVersion") ? root.getInt("MinecraftDataVersion") : 0;
        metadata = root.getCompound("Metadata");
        NbtCompound regionsTag = root.getCompound("Regions");
        for (String key : regionsTag.getKeys()) {
            NbtCompound regionTag = regionsTag.getCompound(key);
            Region region = parseRegion(key, regionTag);
            regions.put(key, region);
        }
    }

    private Region parseRegion(String name, NbtCompound tag) {
        Region region = new Region();
        region.name = name;
        NbtCompound pos = tag.getCompound("Position");
        region.posX = pos.getInt("x");
        region.posY = pos.getInt("y");
        region.posZ = pos.getInt("z");
        NbtCompound size = tag.getCompound("Size");
        region.sizeX = size.getInt("x");
        region.sizeY = size.getInt("y");
        region.sizeZ = size.getInt("z");

        NbtList paletteList = tag.getList("BlockStatePalette", 10);
        for (int i = 0; i < paletteList.size(); i++) {
            NbtCompound entry = paletteList.getCompound(i);
            String blockName = entry.getString("Name");
            NbtCompound props = entry.contains("Properties") ? entry.getCompound("Properties") : null;
            region.palette.add(new PaletteEntry(blockName, props));
        }

        int volume = region.getVolume();
        if (volume > 0 && tag.contains("BlockStates")) {
            long[] packedData = tag.getLongArray("BlockStates");
            int bits = Math.max(2, (int) Math.ceil(Math.log(Math.max(1, region.palette.size())) / Math.log(2)));
            region.blockIndices = unpack(packedData, bits, volume);
        } else {
            region.blockIndices = new int[Math.max(0, volume)];
        }

        if (tag.contains("Entities")) {
            region.entities = tag.getList("Entities", 10);
        }
        if (tag.contains("TileEntities")) {
            region.tileEntities = tag.getList("TileEntities", 10);
        }
        return region;
    }

    static int[] unpack(long[] data, int bits, int count) {
        int[] result = new int[count];
        long mask = (1L << bits) - 1;
        int perLong = 64 / bits;
        for (int i = 0; i < count; i++) {
            int li = i / perLong;
            if (li >= data.length) break;
            int off = (i % perLong) * bits;
            result[i] = (int) ((data[li] >>> off) & mask);
        }
        return result;
    }

    public Map<String, Region> getRegions() {
        return regions;
    }

    public int getVersion() {
        return version;
    }

    public int getMinecraftDataVersion() {
        return minecraftDataVersion;
    }

    public NbtCompound getMetadata() {
        return metadata;
    }

    public Set<String> getAllBlockTypes() {
        Set<String> names = new TreeSet<>();
        for (Region r : regions.values()) {
            for (PaletteEntry p : r.palette) {
                names.add(p.name);
            }
        }
        return names;
    }

    public Map<String, Integer> getBlockCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Region r : regions.values()) {
            for (int idx : r.blockIndices) {
                if (idx < r.palette.size()) {
                    String name = r.palette.get(idx).name;
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        return counts;
    }
}
