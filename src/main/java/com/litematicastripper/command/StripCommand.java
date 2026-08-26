package com.litematicastripper.command;

import com.litematicastripper.core.BlockFilter;
import com.litematicastripper.core.LitematicReader;
import com.litematicastripper.core.LitematicWriter;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class StripCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("stripper")
                .then(literal("list")
                    .executes(ctx -> listBlocks(ctx.getSource())))
                .then(literal("keep")
                    .then(argument("file", StringArgumentType.string())
                        .then(argument("blocks", StringArgumentType.greedyString())
                            .executes(ctx -> filterKeep(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "file"),
                                StringArgumentType.getString(ctx, "blocks"))))))
                .then(literal("remove")
                    .then(argument("file", StringArgumentType.string())
                        .then(argument("blocks", StringArgumentType.greedyString())
                            .executes(ctx -> filterRemove(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "file"),
                                StringArgumentType.getString(ctx, "blocks"))))))
            );
        });
    }

    private static int listBlocks(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("用法:")
            .formatted(Formatting.GOLD), false);
        source.sendFeedback(() -> Text.literal(
            "  /stripper keep <文件名.litematic> <方块ID或通配符>")
            .formatted(Formatting.YELLOW), false);
        source.sendFeedback(() -> Text.literal(
            "  /stripper remove <文件名.litematic> <方块ID或通配符>")
            .formatted(Formatting.YELLOW), false);
        source.sendFeedback(() -> Text.literal(
            "示例: /stripper keep 投影.litematic cherry_log")
            .formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal(
            "示例: /stripper remove 投影.litematic *_leaves")
            .formatted(Formatting.AQUA), false);
        return 1;
    }

    private static int filterKeep(ServerCommandSource source, String filename, String blocks) {
        return doFilter(source, filename, blocks, true);
    }

    private static int filterRemove(ServerCommandSource source, String filename, String blocks) {
        return doFilter(source, filename, blocks, false);
    }

    private static int doFilter(ServerCommandSource source, String filename, String blockPatterns, boolean isKeep) {
        try {
            File inputFile = resolveFile(filename);
            if (!inputFile.exists()) {
                source.sendError(Text.literal("文件不存在: " + inputFile.getAbsolutePath()));
                return 0;
            }

            // Load schematic
            LitematicReader reader = new LitematicReader();
            reader.load(inputFile);

            // Build filter
            BlockFilter filter = new BlockFilter();
            for (String pattern : blockPatterns.split(",")) {
                pattern = pattern.trim();
                if (isKeep) {
                    filter.addKeepPattern(pattern);
                } else {
                    filter.addRemovePattern(pattern);
                }
            }

            // Apply filter
            BlockFilter.FilterResult result = filter.apply(reader);

            // Generate output filename
            String baseName = inputFile.getName().replaceFirst("\\.litematic$", "");
            String suffix = isKeep ? "_kept_" : "_removed_";
            String shortPattern = blockPatterns.replaceAll("[*?]", "").replaceAll("[^a-zA-Z0-9_]", "_");
            if (shortPattern.length() > 30) shortPattern = shortPattern.substring(0, 30);
            File outputFile = new File(inputFile.getParentFile(), baseName + suffix + shortPattern + ".litematic");

            // Write result
            LitematicWriter writer = new LitematicWriter(reader);
            writer.write(outputFile, result.indexMapping, result.newPalettes);

            // Report results
            Map<String, Integer> beforeCounts = reader.getBlockCounts();
            long totalBefore = beforeCounts.values().stream().mapToInt(Integer::intValue).sum();

            source.sendFeedback(() -> Text.literal("✓ 投影剥离完成!")
                .formatted(Formatting.GREEN), false);
            source.sendFeedback(() -> Text.literal("  输入: " + inputFile.getName()), false);
            source.sendFeedback(() -> Text.literal("  输出: " + outputFile.getName()), false);
            source.sendFeedback(() -> Text.literal("  模式: " + (isKeep ? "保留" : "移除") + " [" + blockPatterns + "]"), false);
            source.sendFeedback(() -> Text.literal("  原始方块数: " + totalBefore), false);

            // Show remaining block types
            source.sendFeedback(() -> Text.literal("  剩余方块类型:"), false);
            for (var entry : beforeCounts.entrySet()) {
                boolean kept = filter.shouldKeep(entry.getKey());
                String status = kept ? "✓" : "✗";
                Formatting color = kept ? Formatting.GREEN : Formatting.RED;
                source.sendFeedback(() -> Text.literal(
                    String.format("    %s %s (%d)", status, entry.getKey(), entry.getValue()))
                    .formatted(color), false);
            }

            return 1;
        } catch (IOException e) {
            source.sendError(Text.literal("IO错误: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            source.sendError(Text.literal("错误: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }
    }

    private static File resolveFile(String filename) {
        // Try relative to schematics directory first
        File litematicaDir = new File("schematics");
        File candidate = new File(litematicaDir, filename);
        if (candidate.exists()) return candidate;

        candidate = new File(litematicaDir, filename + ".litematic");
        if (candidate.exists()) return candidate;

        // Try as-is
        return new File(filename);
    }
}
