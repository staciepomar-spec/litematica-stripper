package com.litematicastripper.core;

import java.util.*;

/**
 * Filters blocks in a LitematicReader by name pattern.
 */
public class BlockFilter {
    private final List<String> keepPatterns = new ArrayList<>();
    private final List<String> removePatterns = new ArrayList<>();
    private String replacement = "minecraft:air";

    public void addKeepPattern(String pattern) {
        keepPatterns.add(pattern.toLowerCase());
    }

    public void addRemovePattern(String pattern) {
        removePatterns.add(pattern.toLowerCase());
    }

    public void setReplacement(String blockName) {
        this.replacement = blockName;
    }

    public boolean shouldKeep(String blockName) {
        String name = blockName.toLowerCase();
        if (!keepPatterns.isEmpty()) {
            return matchesAny(name, keepPatterns);
        } else if (!removePatterns.isEmpty()) {
            return !matchesAny(name, removePatterns);
        }
        return true;
    }

    private boolean matchesAny(String blockName, List<String> patterns) {
        for (String pattern : patterns) {
            if (matches(blockName, pattern)) return true;
        }
        return false;
    }

    private boolean matches(String blockName, String pattern) {
        // Full namespace match
        if (blockName.equals(pattern)) return true;

        // Path-only match
        String blockPath = blockName.contains(":")
            ? blockName.substring(blockName.indexOf(':') + 1)
            : blockName;
        String patternPath = pattern.contains(":")
            ? pattern.substring(pattern.indexOf(':') + 1)
            : pattern;

        if (blockPath.equals(patternPath)) return true;

        // Wildcard support
        if (pattern.contains("*") || pattern.contains("?")) {
            if (globMatch(blockPath, patternPath)) return true;
            if (globMatch(blockName, pattern)) return true;
        }

        return false;
    }

    private boolean globMatch(String text, String glob) {
        return globToRegex(glob).matcher(text).matches();
    }

    private java.util.regex.Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> {
                    if ("\\.^$[](){}+|".indexOf(c) >= 0) sb.append('\\');
                    sb.append(c);
                }
            }
        }
        return java.util.regex.Pattern.compile(sb.toString());
    }

    /**
     * Apply this filter to a loaded schematic and produce output data
     * suitable for LitematicWriter.
     *
     * @return indexMapping per region, newPalettes per region
     */
    public FilterResult apply(LitematicReader reader) {
        Map<String, int[]> allMappings = new LinkedHashMap<>();
        Map<String, List<LitematicReader.PaletteEntry>> allNewPalettes = new LinkedHashMap<>();

        for (var regionEntry : reader.getRegions().entrySet()) {
            var region = regionEntry.getValue();

            // Determine which palette entries to keep
            int[] mapping = new int[region.palette.size()];
            Arrays.fill(mapping, -1);

            List<LitematicReader.PaletteEntry> newPalette = new ArrayList<>();

            for (int oldIdx = 0; oldIdx < region.palette.size(); oldIdx++) {
                var entry = region.palette.get(oldIdx);
                if (shouldKeep(entry.name)) {
                    int newIdx = newPalette.size();
                    mapping[oldIdx] = newIdx;
                    newPalette.add(new LitematicReader.PaletteEntry(entry.name, entry.properties));
                }
            }

            // Ensure air is in the new palette
            boolean hasAir = false;
            for (var entry : newPalette) {
                if (entry.name.equals(replacement)) {
                    hasAir = true;
                    break;
                }
            }
            if (!hasAir) {
                newPalette.add(new LitematicReader.PaletteEntry(replacement, null));
            }

            // Fill mapping for removed entries -> point to replacement
            int replacementIdx = findReplacementIndex(newPalette);
            for (int oldIdx = 0; oldIdx < region.palette.size(); oldIdx++) {
                if (mapping[oldIdx] < 0) {
                    mapping[oldIdx] = replacementIdx;
                }
            }

            // Ensure at least one entry
            if (newPalette.isEmpty()) {
                newPalette.add(new LitematicReader.PaletteEntry(replacement, null));
            }

            allMappings.put(regionEntry.getKey(), mapping);
            allNewPalettes.put(regionEntry.getKey(), newPalette);
        }

        return new FilterResult(allMappings, allNewPalettes);
    }

    private int findReplacementIndex(List<LitematicReader.PaletteEntry> palette) {
        for (int i = 0; i < palette.size(); i++) {
            if (palette.get(i).name.equals(replacement)) return i;
        }
        return 0;
    }

    public static class FilterResult {
        public final Map<String, int[]> indexMapping;
        public final Map<String, List<LitematicReader.PaletteEntry>> newPalettes;

        FilterResult(Map<String, int[]> mapping,
                     Map<String, List<LitematicReader.PaletteEntry>> palettes) {
            this.indexMapping = mapping;
            this.newPalettes = palettes;
        }
    }
}
