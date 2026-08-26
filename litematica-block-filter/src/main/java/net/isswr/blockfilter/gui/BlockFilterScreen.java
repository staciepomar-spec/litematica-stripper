package net.isswr.blockfilter.gui;

import net.isswr.blockfilter.BlockFilterManager;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BlockFilterScreen extends Screen
{
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private final List<Block> materialBlocks = new ArrayList<>();
    private final List<Block> filteredBlocks = new ArrayList<>();
    private TextFieldWidget searchBox;
    private ButtonWidget toggleButton;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int scrollOffset;

    public BlockFilterScreen(Screen parent)
    {
        super(Text.literal("实时方块过滤"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        this.listLeft = Math.max(20, this.width / 2 - 220);
        this.listRight = Math.min(this.width - 20, this.listLeft + 440);
        this.listTop = 84;
        this.listBottom = this.height - 64;
        this.loadSchematicMaterials();

        this.searchBox = new TextFieldWidget(this.textRenderer, this.listLeft, 30,
                this.listRight - this.listLeft, 18, Text.literal("搜索"));
        this.searchBox.setChangedListener(text -> this.applySearch());
        this.addDrawableChild(this.searchBox);

        int buttonX = this.listRight;
        int backX = buttonX - 70;
        buttonX = backX - 6;
        int refreshX = buttonX - 60;
        buttonX = refreshX - 6;
        int noneX = buttonX - 80;
        buttonX = noneX - 6;
        int allX = buttonX - 60;
        buttonX = allX - 6;
        int toggleX = buttonX - 90;

        this.toggleButton = ButtonWidget.builder(this.getToggleLabel(), button ->
                BlockFilterManager.toggleEnabled()).dimensions(toggleX, 52, 90, 20).build();
        this.addDrawableChild(this.toggleButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("全选"), button ->
                BlockFilterManager.setSelected(this.filteredBlocks, true)).dimensions(allX, 52, 60, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("全不选"), button ->
                BlockFilterManager.setSelected(this.filteredBlocks, false)).dimensions(noneX, 52, 80, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("刷新材料"), button -> {
            this.loadSchematicMaterials();
            this.applySearch();
        }).dimensions(refreshX, 52, 60, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> this.close()).
                dimensions(backX, 52, 70, 20).build());

        this.applySearch();
    }

    private void loadSchematicMaterials()
    {
        BlockFilterManager.refreshSchematicMaterials();
        this.materialBlocks.clear();

        for (String blockId : BlockFilterManager.getSchematicBlockIds())
        {
            Registries.BLOCK.getOrEmpty(Identifier.of(blockId)).ifPresent(this.materialBlocks::add);
        }

        this.materialBlocks.sort(Comparator.comparing(BlockFilterManager::getDisplayName)
                .thenComparing(BlockFilterManager::getBlockId));
    }

    private Text getToggleLabel()
    {
        return BlockFilterManager.isEnabled() ? Text.literal("已启用") : Text.literal("已停用");
    }

    private void applySearch()
    {
        this.filteredBlocks.clear();
        String query = this.searchBox.getText().trim().toLowerCase(Locale.ROOT);

        for (Block block : this.materialBlocks)
        {
            String id = BlockFilterManager.getBlockId(block);
            String name = BlockFilterManager.getDisplayName(block);

            if (query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query) || name.toLowerCase(Locale.ROOT).contains(query))
            {
                this.filteredBlocks.add(block);
            }
        }

        this.scrollOffset = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        context.fillGradient(0, 0, this.width, this.height, 0xD0101010, 0xE0202020);
        super.render(context, mouseX, mouseY, delta);

        this.toggleButton.setMessage(this.getToggleLabel());
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        context.fill(this.listLeft, this.listTop, this.listRight, this.listBottom, 0xC0080810);
        context.drawBorder(this.listLeft, this.listTop, this.listRight - this.listLeft,
                this.listBottom - this.listTop, 0xFF555555);

        int rows = this.getRowCount();
        this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0,
                Math.max(0, this.filteredBlocks.size() - rows));

        for (int row = 0; row < Math.min(rows, this.filteredBlocks.size()); ++row)
        {
            Block block = this.filteredBlocks.get(this.scrollOffset + row);
            int y = this.listTop + 4 + row * ROW_HEIGHT;
            boolean selected = BlockFilterManager.isSelected(block);

            if (mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= y && mouseY < y + ROW_HEIGHT)
            {
                context.fill(this.listLeft + 1, y, this.listRight - 1, y + ROW_HEIGHT, 0x20FFFFFF);
            }

            context.drawItem(new ItemStack(block), this.listLeft + 6, y + 3);
            String label = this.truncate(BlockFilterManager.getDisplayName(block),
                    this.listRight - this.listLeft - 82);
            context.drawTextWithShadow(this.textRenderer, label, this.listLeft + 32, y + 7,
                    selected ? 0xFFFFFFFF : 0xFFAAAAAA);
            context.drawTextWithShadow(this.textRenderer, selected ? "显示" : "隐藏",
                    this.listRight - 38, y + 7, selected ? 0xFF55FF55 : 0xFFFF5555);
        }

        int selectedCount = 0;

        for (Block block : this.materialBlocks)
        {
            if (BlockFilterManager.isSelected(block))
            {
                ++selectedCount;
            }
        }

        String status = this.materialBlocks.isEmpty() ? "未加载到投影材料"
                : "已选 " + selectedCount + " / " + this.materialBlocks.size()
                + "，当前匹配 " + this.filteredBlocks.size() + " 个";
        context.drawTextWithShadow(this.textRenderer, status, this.listLeft, this.listBottom + 8, 0xFFCCCCCC);
    }

    private int getRowCount()
    {
        return Math.max(1, (this.listBottom - this.listTop - 8) / ROW_HEIGHT);
    }

    private String truncate(String value, int maxWidth)
    {
        if (this.textRenderer.getWidth(value) <= maxWidth)
        {
            return value;
        }

        while (value.length() > 1 && this.textRenderer.getWidth(value + "...") > maxWidth)
        {
            value = value.substring(0, value.length() - 1);
        }

        return value + "...";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= this.listTop && mouseY < this.listBottom)
        {
            int rows = this.getRowCount();
            int relativeRow = (int) ((mouseY - this.listTop - 4) / ROW_HEIGHT);
            int index = this.scrollOffset + relativeRow;

            if (relativeRow >= 0 && relativeRow < rows && index >= 0 && index < this.filteredBlocks.size())
            {
                Block block = this.filteredBlocks.get(index);
                BlockFilterManager.setSelected(block, BlockFilterManager.isSelected(block) == false);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        if (mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= this.listTop && mouseY < this.listBottom)
        {
            this.scrollOffset -= (int) (verticalAmount * ROW_HEIGHT);
            this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0,
                    Math.max(0, this.filteredBlocks.size() - this.getRowCount()));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close()
    {
        if (this.client != null)
        {
            this.client.setScreen(this.parent);
        }
    }
}
