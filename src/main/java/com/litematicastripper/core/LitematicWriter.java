package com.litematicastripper.core;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LitematicWriter {

    private final LitematicReader source;

    public LitematicWriter(LitematicReader source) {
        this.source = source;
    }

    public void write(File output,
                      Map<String, int[]> indexMapping,
                      Map<String, List<LitematicReader.PaletteEntry>> newPalettes) throws IOException {

        NbtCompound root = new NbtCompound();
        root.putInt("Version", source.getVersion());
        root.putInt("MinecraftDataVersion", source.getMinecraftDataVersion());
        root.putInt("SubVersion", 1);

        // Build metadata
        NbtCompound meta = new NbtCompound();
        NbtCompound sourceMeta = source.getMetadata();
        meta.putString("Name", sourceMeta.contains("Name") ? sourceMeta.getString("Name") : "Stripper Output");
        meta.putString("Author", sourceMeta.contains("Author") ? sourceMeta.getString("Author") : "");
        meta.putString("Description", sourceMeta.contains("Description") ? sourceMeta.getString("Description") : "");

        long totalBlocks = 0;
        long totalVolume = 0;
        int minTotalX = Integer.MAX_VALUE, minTotalY = Integer.MAX_VALUE, minTotalZ = Integer.MAX_VALUE;
        int maxTotalX = Integer.MIN_VALUE, maxTotalY = Integer.MIN_VALUE, maxTotalZ = Integer.MIN_VALUE;

        NbtCompound regionsTag = new NbtCompound();
        for (var entry : source.getRegions().entrySet()) {
            String regionName = entry.getKey();
            var region = entry.getValue();

            NbtCompound regionTag = buildRegion(region, indexMapping.get(regionName), newPalettes.get(regionName));
            regionsTag.put(regionName, regionTag);

            totalVolume += (long) Math.abs(region.sizeX) * Math.abs(region.sizeY) * Math.abs(region.sizeZ);

            // Count non-air blocks
            List<LitematicReader.PaletteEntry> newPalette = newPalettes.get(regionName);
            int[] mapping = indexMapping.get(regionName);
            if (mapping != null && newPalette != null) {
                for (int oldIdx : region.blockIndices) {
                    if (oldIdx < mapping.length) {
                        int newIdx = mapping[oldIdx];
                        if (newIdx >= 0 && newIdx < newPalette.size()) {
                            String blockName = newPalette.get(newIdx).name;
                            if (!blockName.equals("minecraft:air") && !blockName.equals("air")) {
                                totalBlocks++;
                            }
                        }
                    }
                }
            }

            // Enclosing size
            int rx = region.posX, ry = region.posY, rz = region.posZ;
            int sx = Math.abs(region.sizeX), sy = Math.abs(region.sizeY), sz = Math.abs(region.sizeZ);
            minTotalX = Math.min(minTotalX, rx);
            minTotalY = Math.min(minTotalY, ry);
            minTotalZ = Math.min(minTotalZ, rz);
            maxTotalX = Math.max(maxTotalX, rx + sx);
            maxTotalY = Math.max(maxTotalY, ry + sy);
            maxTotalZ = Math.max(maxTotalZ, rz + sz);
        }

        meta.putLong("TotalBlocks", totalBlocks);
        meta.putLong("TotalVolume", totalVolume);
        meta.putInt("RegionCount", source.getRegions().size());

        NbtCompound enclosingSize = new NbtCompound();
        enclosingSize.putInt("x", maxTotalX - minTotalX);
        enclosingSize.putInt("y", maxTotalY - minTotalY);
        enclosingSize.putInt("z", maxTotalZ - minTotalZ);
        meta.put("EnclosingSize", enclosingSize);

        // Preview image data (required by Litematica, can be empty)
        meta.putByteArray("PreviewImageData", new byte[0]);

        root.put("Metadata", meta);
        root.put("Regions", regionsTag);

        // Write as gzip-compressed NBT (most compatible with Litematica)
        try (FileOutputStream fos = new FileOutputStream(output)) {
            NbtIo.writeCompressed(root, fos);
        }
    }

    private NbtCompound buildRegion(LitematicReader.Region region,
                                     int[] mapping,
                                     List<LitematicReader.PaletteEntry> newPalette) {
        NbtCompound tag = new NbtCompound();

        NbtCompound pos = new NbtCompound();
        pos.putInt("x", region.posX);
        pos.putInt("y", region.posY);
        pos.putInt("z", region.posZ);
        tag.put("Position", pos);

        NbtCompound size = new NbtCompound();
        size.putInt("x", region.sizeX);
        size.putInt("y", region.sizeY);
        size.putInt("z", region.sizeZ);
        tag.put("Size", size);

        // Palette
        NbtList paletteList = new NbtList();
        for (var entry : newPalette) {
            NbtCompound entryTag = new NbtCompound();
            entryTag.putString("Name", entry.name);
            if (entry.properties != null && !entry.properties.isEmpty()) {
                entryTag.put("Properties", entry.properties.copy());
            }
            paletteList.add(entryTag);
        }
        tag.put("BlockStatePalette", paletteList);

        // Remap block indices
        int[] remapped = new int[region.blockIndices.length];
        for (int i = 0; i < region.blockIndices.length; i++) {
            int oldIdx = region.blockIndices[i];
            if (mapping != null && oldIdx < mapping.length && mapping[oldIdx] >= 0) {
                remapped[i] = mapping[oldIdx];
            } else {
                remapped[i] = 0;
            }
        }

        // Pack block states with correct bit width
        int paletteSize = Math.max(1, newPalette.size());
        int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(0, paletteSize - 1)));
        long[] packed = pack(remapped, bits);
        tag.putLongArray("BlockStates", packed);

        // Entities and TileEntities
        tag.put("Entities", region.entities != null ? region.entities.copy() : new NbtList());
        tag.put("TileEntities", region.tileEntities != null ? region.tileEntities.copy() : new NbtList());
        tag.put("PendingBlockTicks", new NbtList());
        tag.put("PendingFluidTicks", new NbtList());

        return tag;
    }

    static long[] pack(int[] values, int bitsPerEntry) {
        int entriesPerLong = 64 / bitsPerEntry;
        int numLongs = (values.length + entriesPerLong - 1) / entriesPerLong;
        long[] result = new long[numLongs];
        long mask = (1L << bitsPerEntry) - 1;

        for (int i = 0; i < values.length; i++) {
            int longIndex = i / entriesPerLong;
            int offset = (i % entriesPerLong) * bitsPerEntry;
            result[longIndex] |= ((long) values[i] & mask) << offset;
        }

        return result;
    }
}
