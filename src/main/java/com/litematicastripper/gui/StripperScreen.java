package com.litematicastripper.gui;

import com.litematicastripper.core.BlockFilter;
import com.litematicastripper.core.LitematicReader;
import com.litematicastripper.core.LitematicWriter;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class StripperScreen extends Screen {

    private List<File> availableFiles = new ArrayList<>();
    private int selectedFileIndex = -1;
    private double fileListScroll = 0;

    private LitematicReader reader;
    private File currentFile;

    private final Map<String, Integer> blockCounts = new LinkedHashMap<>();
    private final List<String> allBlockNames = new ArrayList<>();
    private final List<String> filteredBlockNames = new ArrayList<>();
    private final Map<String, Boolean> blockSelection = new LinkedHashMap<>();
    private final Map<String, ItemStack> blockIcons = new HashMap<>();
    private final Map<String, String> blockDisplayNames = new HashMap<>();

    private String searchFilter = "";
    private double blockListScroll = 0;
    private static final int FILE_ROW_HEIGHT = 18;
    private static final int BLOCK_ROW_HEIGHT = 22;
    private static final int ICON_SIZE = 16;

    private int panelLeft, panelRight, filePanelTop, filePanelBottom;
    private int blockPanelTop, blockPanelBottom;
    private int fileListWidth = 140;

    private TextFieldWidget searchField;
    private ButtonWidget exportButton;
    private TextFieldWidget exportNameField;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;

    public StripperScreen() {
        super(Text.literal("投影剥离"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        panelLeft = centerX - 200;
        panelRight = centerX + 200;
        filePanelTop = 30;
        filePanelBottom = this.height - 90;
        blockPanelTop = 30;
        blockPanelBottom = this.height - 90;

        scanSchematicsDirectory();
        blockDisplayNames.clear();

        searchField = new TextFieldWidget(this.textRenderer, panelLeft + fileListWidth + 10, this.height - 82,
            panelRight - panelLeft - fileListWidth - 20, 18, Text.literal("搜索方块"));
        searchField.setPlaceholder(Text.literal("输入方块名筛选..."));
        searchField.setChangedListener(text -> {
            searchFilter = text.toLowerCase();
            applySearchFilter();
        });
        addDrawableChild(searchField);

        exportButton = ButtonWidget.builder(Text.literal("导出投影"), btn -> exportFiltered())
            .dimensions(centerX + 50, this.height - 55, 100, 20).build();
        exportButton.active = false;
        addDrawableChild(exportButton);

        exportNameField = new TextFieldWidget(this.textRenderer, centerX - 160, this.height - 55,
            200, 20, Text.literal("导出名称"));
        exportNameField.setPlaceholder(Text.literal("自定义导出名称..."));
        addDrawableChild(exportNameField);

        ButtonWidget selectAllButton = ButtonWidget.builder(Text.literal("全选"), btn -> setAllBlocksSelected(true))
            .dimensions(centerX - 160, this.height - 28, 90, 20).build();
        addDrawableChild(selectAllButton);

        ButtonWidget selectNoneButton = ButtonWidget.builder(Text.literal("全不选"), btn -> setAllBlocksSelected(false))
            .dimensions(centerX - 60, this.height - 28, 90, 20).build();
        addDrawableChild(selectNoneButton);

        ButtonWidget backButton = ButtonWidget.builder(Text.literal("返回"), btn -> close())
            .dimensions(centerX + 40, this.height - 28, 80, 20).build();
        addDrawableChild(backButton);
    }

    private void scanSchematicsDirectory() {
        availableFiles.clear();
        File schematicsDir = new File("schematics");
        if (schematicsDir.exists() && schematicsDir.isDirectory()) {
            File[] files = schematicsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".litematic"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                availableFiles.addAll(Arrays.asList(files));
            }
        }
        File cwd = new File(".");
        File[] cwdFiles = cwd.listFiles((dir, name) -> name.toLowerCase().endsWith(".litematic"));
        if (cwdFiles != null) {
            for (File f : cwdFiles) {
                if (!availableFiles.contains(f)) availableFiles.add(f);
            }
        }
    }

    private void selectFile(int index) {
        if (index < 0 || index >= availableFiles.size()) return;
        selectedFileIndex = index;
        currentFile = availableFiles.get(index);
        try {
            reader = new LitematicReader();
            reader.load(currentFile);
            blockCounts.clear();
            allBlockNames.clear();
            filteredBlockNames.clear();
            blockSelection.clear();
            blockIcons.clear();
            blockDisplayNames.clear();
            Map<String, Integer> counts = reader.getBlockCounts();
            for (var entry : counts.entrySet()) {
                if (entry.getKey().equals("minecraft:air")) continue;
                allBlockNames.add(entry.getKey());
                blockCounts.put(entry.getKey(), entry.getValue());
                blockSelection.put(entry.getKey(), true);
            }
            Collections.sort(allBlockNames);
            filteredBlockNames.addAll(allBlockNames);
            exportButton.active = !allBlockNames.isEmpty();
            blockListScroll = 0;
            setStatus("✓ " + currentFile.getName(), 0x55FF55);
        } catch (Exception e) {
            setStatus("加载失败: " + e.getMessage(), 0xFF5555);
            reader = null;
            currentFile = null;
        }
    }

    private void applySearchFilter() {
        filteredBlockNames.clear();
        for (String name : allBlockNames) {
            if (matchesSearch(name)) {
                filteredBlockNames.add(name);
            }
        }
        blockListScroll = 0;
    }

    private boolean matchesSearch(String blockName) {
        if (searchFilter.isEmpty()) return true;
        return blockName.toLowerCase().contains(searchFilter)
            || getBlockDisplayName(blockName).toLowerCase().contains(searchFilter);
    }

    private void setAllBlocksSelected(boolean selected) {
        if (reader == null) return;
        for (String name : allBlockNames) {
            blockSelection.put(name, selected);
        }
        setStatus(selected ? "已全选所有方块" : "已取消选择所有方块", 0xCCCCCC);
    }

    private void exportFiltered() {
        if (reader == null || currentFile == null) return;
        try {
            BlockFilter filter = new BlockFilter();
            for (String name : allBlockNames) {
                if (!blockSelection.getOrDefault(name, false)) {
                    filter.addRemovePattern(name);
                }
            }
            BlockFilter.FilterResult result = filter.apply(reader);
            String customName = exportNameField.getText().trim();
            String baseName;
            if (!customName.isEmpty()) {
                baseName = customName;
            } else {
                baseName = currentFile.getName().replaceFirst("\\.litematic$", "") + "_剥离";
            }
            File outputFile = new File(currentFile.getParentFile(), baseName + ".litematic");
            LitematicWriter writer = new LitematicWriter(reader);
            writer.write(outputFile, result.indexMapping, result.newPalettes);
            long selectedCount = blockSelection.values().stream().filter(Boolean::booleanValue).count();
            setStatus("✓ 已导出: " + outputFile.getName() + " (保留 " + selectedCount + " 种)", 0x55FF55);
        } catch (IOException e) {
            setStatus("导出失败: " + e.getMessage(), 0xFF5555);
        }
    }

    private void setStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = color;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
            this.width / 2, 8, 0xFFFFFF);
        renderFileList(context, mouseX, mouseY);
        renderBlockList(context, mouseX, mouseY);
        if (!statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(statusMessage), this.width / 2, this.height - 70, statusColor);
        }
        if (reader != null && !allBlockNames.isEmpty()) {
            long sel = blockSelection.values().stream().filter(Boolean::booleanValue).count();
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(String.format("已选 %d / %d", sel, allBlockNames.size())),
                this.width / 2, this.height - 40, 0xCCCCCC);
        }
    }

    private void renderFileList(DrawContext context, int mouseX, int mouseY) {
        int x = panelLeft;
        int y = filePanelTop;
        int w = fileListWidth;
        int h = filePanelBottom - filePanelTop;
        context.fill(x, y, x + w, y + h, 0xC0101010);
        context.drawBorder(x, y, w, h, 0xFF444444);
        context.fill(x, y, x + w, y + 16, 0xFF333333);
        context.drawTextWithShadow(textRenderer, "投影文件 (" + availableFiles.size() + ")",
            x + 4, y + 4, 0xCCCCCC);
        int listStart = y + 18;
        int listHeight = h - 20;
        int maxVisible = listHeight / FILE_ROW_HEIGHT;
        if (availableFiles.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "schematics/",
                x + 4, listStart + 4, 0x888888);
            context.drawTextWithShadow(textRenderer, "无文件",
                x + 4, listStart + 20, 0x666666);
            return;
        }
        int firstVisible = (int) (fileListScroll / FILE_ROW_HEIGHT);
        int visibleCount = Math.min(maxVisible, availableFiles.size());
        for (int row = 0; row < visibleCount; row++) {
            int fileIdx = firstVisible + row;
            if (fileIdx >= availableFiles.size()) break;
            File f = availableFiles.get(fileIdx);
            int rowY = listStart + row * FILE_ROW_HEIGHT;
            boolean isSelected = fileIdx == selectedFileIndex;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + FILE_ROW_HEIGHT;
            if (isSelected) {
                context.fill(x + 1, rowY, x + w - 1, rowY + FILE_ROW_HEIGHT, 0xFF2A4A2A);
            } else if (hovered) {
                context.fill(x + 1, rowY, x + w - 1, rowY + FILE_ROW_HEIGHT, 0x20FFFFFF);
            }
            String name = f.getName().replace(".litematic", "");
            if (textRenderer.getWidth(name) > w - 10) {
                while (textRenderer.getWidth(name + "..") > w - 10 && name.length() > 1) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "..";
            }
            context.drawTextWithShadow(textRenderer, name,
                x + 4, rowY + 3, isSelected ? 0x55FF55 : (hovered ? 0xFFFFFF : 0xBBBBBB));
        }
        if (availableFiles.size() > maxVisible) {
            renderScrollbar(context, x + w - 4, listStart, 3, listHeight,
                fileListScroll, Math.max(1, (availableFiles.size() - maxVisible) * FILE_ROW_HEIGHT),
                maxVisible, availableFiles.size());
        }
    }

    private void renderBlockList(DrawContext context, int mouseX, int mouseY) {
        int x = panelLeft + fileListWidth + 6;
        int y = blockPanelTop;
        int w = panelRight - x;
        int h = blockPanelBottom - blockPanelTop;
        context.fill(x, y, x + w, y + h, 0xC0101010);
        context.drawBorder(x, y, w, h, 0xFF444444);
        context.fill(x, y, x + w, y + 16, 0xFF333333);
        String header = reader != null
            ? String.format("方块列表 (%d 种)", filteredBlockNames.size())
            : "方块列表";
        context.drawTextWithShadow(textRenderer, header, x + 4, y + 4, 0xCCCCCC);
        if (reader == null || filteredBlockNames.isEmpty()) {
            String hint = reader == null ? "← 选择投影文件" :
                (allBlockNames.isEmpty() ? "无方块数据" : "无匹配结果");
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(hint).formatted(Formatting.GRAY),
                x + w / 2, y + h / 2, 0xFFFFFFFF);
            return;
        }
        int listStart = y + 18;
        int listHeight = h - 20;
        int maxVisible = listHeight / BLOCK_ROW_HEIGHT;
        int firstVisible = (int) (blockListScroll / BLOCK_ROW_HEIGHT);
        int visibleCount = Math.min(maxVisible, filteredBlockNames.size());
        for (int row = 0; row < visibleCount; row++) {
            int blockIdx = firstVisible + row;
            if (blockIdx >= filteredBlockNames.size()) break;
            String name = filteredBlockNames.get(blockIdx);
            boolean selected = blockSelection.getOrDefault(name, false);
            int rowY = listStart + row * BLOCK_ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + BLOCK_ROW_HEIGHT;
            if (hovered) {
                context.fill(x + 1, rowY, x + w - 1, rowY + BLOCK_ROW_HEIGHT, 0x20FFFFFF);
            }
            int boxX = x + 5;
            int boxY = rowY + (BLOCK_ROW_HEIGHT - ICON_SIZE) / 2;
            context.fill(boxX, boxY, boxX + ICON_SIZE, boxY + ICON_SIZE, 0xFF2A2A2A);
            context.drawItem(getBlockIcon(name), boxX, boxY);
            if (selected) {
                context.drawBorder(boxX - 1, boxY - 1, ICON_SIZE + 2, ICON_SIZE + 2, 0xFF55FF55);
            }
            String displayName = getBlockDisplayName(name);
            if (textRenderer.getWidth(displayName) > w - 60) {
                while (textRenderer.getWidth(displayName + "..") > w - 60 && displayName.length() > 1) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "..";
            }
            context.drawTextWithShadow(textRenderer, displayName,
                boxX + ICON_SIZE + 5, rowY + (BLOCK_ROW_HEIGHT - 8) / 2, selected ? 0xFFFFFF : 0x888888);
            int count = blockCounts.getOrDefault(name, 0);
            String countStr = formatCount(count);
            context.drawTextWithShadow(textRenderer, countStr,
                x + w - textRenderer.getWidth(countStr) - 6, rowY + (BLOCK_ROW_HEIGHT - 8) / 2, 0xAAAAAA);
        }
        if (filteredBlockNames.size() > maxVisible) {
            renderScrollbar(context, x + w - 4, listStart, 3, listHeight,
                blockListScroll, Math.max(1, (filteredBlockNames.size() - maxVisible) * BLOCK_ROW_HEIGHT),
                maxVisible, filteredBlockNames.size());
        }
    }

    private String getBlockDisplayName(String blockName) {
        return blockDisplayNames.computeIfAbsent(blockName, name -> {
            String translationKey = "block." + name.replace(":", ".");
            String displayName = Text.translatable(translationKey).getString();
            if (displayName.equals(translationKey)) {
                displayName = name.replaceFirst("^minecraft:", "");
            }
            return displayName;
        });
    }

    private ItemStack getBlockIcon(String blockName) {
        return blockIcons.computeIfAbsent(blockName, name -> {
            Identifier identifier = Identifier.of(name);
            return Registries.BLOCK.getOrEmpty(identifier)
                .map(Block::asItem)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        });
    }

    private void renderScrollbar(DrawContext context, int x, int y, int w, int h,
                                 double scroll, int maxScroll, int visible, int total) {
        if (maxScroll <= 0) return;
        context.fill(x, y, x + w, y + h, 0xFF1A1A1A);
        int thumbH = Math.max(8, h * visible / total);
        double ratio = Math.min(1.0, scroll / maxScroll);
        int thumbY = y + (int) (ratio * (h - thumbH));
        context.fill(x, thumbY, x + w, thumbY + thumbH, 0xFF555555);
    }

    private String formatCount(int count) {
        if (count >= 1000000) return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000) return String.format("%.1fK", count / 1000.0);
        return String.valueOf(count);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int fileX = panelLeft;
        int fileY = filePanelTop + 18;
        if (mouseX >= fileX && mouseX < fileX + fileListWidth && mouseY >= fileY && mouseY < filePanelBottom) {
            int maxVisible = (filePanelBottom - fileY) / FILE_ROW_HEIGHT;
            int firstVisible = (int) (fileListScroll / FILE_ROW_HEIGHT);
            int row = (int) ((mouseY - fileY) / FILE_ROW_HEIGHT);
            int fileIdx = firstVisible + row;
            if (fileIdx >= 0 && fileIdx < availableFiles.size() && row < maxVisible) {
                selectFile(fileIdx);
                return true;
            }
        }
        int blockX = panelLeft + fileListWidth + 6;
        int blockY = blockPanelTop + 18;
        int blockW = panelRight - blockX;
        if (mouseX >= blockX && mouseX < blockX + blockW && mouseY >= blockY && mouseY < blockPanelBottom
            && reader != null && !filteredBlockNames.isEmpty()) {
            int maxVisible = (blockPanelBottom - blockY) / BLOCK_ROW_HEIGHT;
            int firstVisible = (int) (blockListScroll / BLOCK_ROW_HEIGHT);
            int row = (int) ((mouseY - blockY) / BLOCK_ROW_HEIGHT);
            int blockIdx = firstVisible + row;
            if (blockIdx >= 0 && blockIdx < filteredBlockNames.size() && row < maxVisible) {
                String name = filteredBlockNames.get(blockIdx);
                blockSelection.put(name, !blockSelection.getOrDefault(name, false));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelLeft && mouseX < panelLeft + fileListWidth
            && mouseY >= filePanelTop && mouseY < filePanelBottom) {
            int maxVisible = (filePanelBottom - filePanelTop - 18) / FILE_ROW_HEIGHT;
            int maxScroll = Math.max(0, (availableFiles.size() - maxVisible) * FILE_ROW_HEIGHT);
            fileListScroll = Math.clamp(fileListScroll - verticalAmount * FILE_ROW_HEIGHT, 0, maxScroll);
            return true;
        }
        if (mouseX >= panelLeft + fileListWidth && mouseX < panelRight
            && mouseY >= blockPanelTop && mouseY < blockPanelBottom) {
            int maxVisible = (blockPanelBottom - blockPanelTop - 18) / BLOCK_ROW_HEIGHT;
            int maxScroll = Math.max(0, (filteredBlockNames.size() - maxVisible) * BLOCK_ROW_HEIGHT);
            blockListScroll = Math.clamp(blockListScroll - verticalAmount * BLOCK_ROW_HEIGHT, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        this.client.setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
