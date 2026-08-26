package net.isswr.blockfilter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import fi.dy.masa.litematica.world.ChunkSchematic;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.world.chunk.ChunkSection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockFilterManager
{
    private static final Set<String> SELECTED_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Set<String> HIDDEN_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SCHEMATIC_BLOCK_IDS = ConcurrentHashMap.newKeySet();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("litematica_block_filter.json");
    private static boolean enabled = true;

    static
    {
        load();
    }

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static boolean toggleEnabled()
    {
        enabled = !enabled;
        save();
        refreshRenderers();
        return enabled;
    }

    public static boolean shouldHide(BlockState state)
    {
        return enabled && SCHEMATIC_BLOCK_IDS.isEmpty() == false && state != null &&
               SELECTED_BLOCKS.contains(getBlockId(state.getBlock())) == false;
    }

    public static String getBlockId(Block block)
    {
        return Registries.BLOCK.getId(block).toString();
    }

    public static String getDisplayName(Block block)
    {
        return Text.translatable(block.getTranslationKey()).getString();
    }

    public static boolean isSelected(Block block)
    {
        return SELECTED_BLOCKS.contains(getBlockId(block));
    }

    public static int getSelectedCount()
    {
        return SELECTED_BLOCKS.size();
    }

    public static void setSelected(Block block, boolean selected)
    {
        String blockId = getBlockId(block);

        if (selected)
        {
            SELECTED_BLOCKS.add(blockId);
            HIDDEN_BLOCKS.remove(blockId);
        }
        else
        {
            SELECTED_BLOCKS.remove(blockId);
            HIDDEN_BLOCKS.add(blockId);
        }

        save();
        refreshRenderers();
    }

    public static void setSelected(Iterable<Block> blocks, boolean selected)
    {
        boolean changed = false;

        for (Block block : blocks)
        {
            String blockId = getBlockId(block);

            if (selected)
            {
                changed |= SELECTED_BLOCKS.add(blockId);
                changed |= HIDDEN_BLOCKS.remove(blockId);
            }
            else
            {
                changed |= SELECTED_BLOCKS.remove(blockId);
                changed |= HIDDEN_BLOCKS.add(blockId);
            }
        }

        if (changed)
        {
            save();
            refreshRenderers();
        }
    }

    public static void refreshRenderers()
    {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().world != null)
            {
                SchematicWorldRefresher.INSTANCE.updateAll();
            }
        });
    }

    public static Set<String> getSchematicBlockIds()
    {
        return Set.copyOf(SCHEMATIC_BLOCK_IDS);
    }

    public static void refreshSchematicMaterials()
    {
        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();

        if (world == null)
        {
            SCHEMATIC_BLOCK_IDS.clear();
            return;
        }

        Set<String> discovered = ConcurrentHashMap.newKeySet();

        for (ChunkSchematic chunk : world.getChunkProvider().getLoadedChunks().values())
        {
            for (ChunkSection section : chunk.getSectionArray())
            {
                if (section == null || section.isEmpty())
                {
                    continue;
                }

                section.getBlockStateContainer().forEachValue(state -> {
                    if (state != null && state.isAir() == false)
                    {
                        discovered.add(getBlockId(state.getBlock()));
                    }
                });
            }
        }

        boolean changed = false;
        changed |= SCHEMATIC_BLOCK_IDS.retainAll(discovered);
        changed |= SCHEMATIC_BLOCK_IDS.addAll(discovered);
        changed |= SELECTED_BLOCKS.removeIf(blockId -> discovered.contains(blockId) == false);

        for (String blockId : discovered)
        {
            if (HIDDEN_BLOCKS.contains(blockId))
            {
                changed |= SELECTED_BLOCKS.remove(blockId);
            }
            else
            {
                changed |= SELECTED_BLOCKS.add(blockId);
            }
        }

        changed |= HIDDEN_BLOCKS.retainAll(discovered);

        if (changed)
        {
            save();
            refreshRenderers();
        }
    }

    private static void load()
    {
        if (Files.exists(CONFIG_FILE) == false)
        {
            return;
        }

        try
        {
            JsonObject root = JsonParser.parseString(Files.readString(CONFIG_FILE)).getAsJsonObject();
            enabled = root.has("enabled") && root.get("enabled").getAsBoolean();

            if (root.has("selected"))
            {
                root.getAsJsonArray("selected").forEach(element ->
                        SELECTED_BLOCKS.add(element.getAsString()));
            }

            if (root.has("hidden"))
            {
                root.getAsJsonArray("hidden").forEach(element ->
                        HIDDEN_BLOCKS.add(element.getAsString()));
            }
        }
        catch (Exception ignored)
        {
        }
    }

    private static void save()
    {
        try
        {
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            JsonArray selected = new JsonArray();
            SELECTED_BLOCKS.stream().sorted().forEach(selected::add);
            root.add("selected", selected);
            JsonArray hidden = new JsonArray();
            HIDDEN_BLOCKS.stream().sorted().forEach(hidden::add);
            root.add("hidden", hidden);
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, root.toString());
        }
        catch (IOException ignored)
        {
        }
    }
}
