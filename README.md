# Litematica Stripper - 投影剥离工具

一个基于 Fabric 的 Minecraft 模组，用于从 Litematica 投影文件中筛选指定建筑结构方块。

## 功能

- **可视化界面**：游戏内按 `O` 键打开图形界面，点击操作即可完成筛选
- **保留模式**：只保留勾选的方块，其余替换为空气
- **方块图标**：直观显示每种方块的图标和数量
- **搜索筛选**：输入方块名快速定位
- **多区域处理**：自动处理投影中的所有区域

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) (>= 0.16.0)
2. 将 `build/libs/[投影剥离] litematica-stripper-1.0.0+mc1.21.1.jar` 放入 `.minecraft/mods/` 目录
3. 同时安装 [Fabric API](https://modrinth.com/mod/fabric-api)

## 使用方法

### 打开界面

进入游戏后按 **`O`** 键打开投影剥离界面。

### 操作步骤

1. **选择投影文件**：左侧面板列出 `schematics` 文件夹中的 `.litematic` 文件，点击选择
2. **勾选方块**：右侧显示该投影中所有方块及数量，点击方块行切换勾选状态
3. **搜索方块**：底部搜索框输入方块名（如 `cherry`）快速筛选
4. **导出**：
   - 可在导出名称框输入自定义文件名（留空则自动命名为 `原名_剥离`）
   - 点击 **「导出投影」** 按钮，只保留勾选的方块

### 快捷按钮

| 按钮 | 功能 |
|------|------|
| 全选 | 勾选所有方块 |
| 全不选 | 取消所有勾选 |
| 返回 | 关闭界面 |

### 文件位置

模组自动扫描 `.minecraft/schematics/` 目录下的 `.litematic` 文件，导出文件保存在同目录。

## 构建

```bash
gradle build
```

构建产物位于 `build/libs/[投影剥离] litematica-stripper-1.0.0+mc1.21.1.jar`

## 项目结构

```
├── src/main/java/com/litematicastripper/
│   ├── LitematicaStripperMod.java    # 模组入口
│   ├── core/
│   │   ├── LitematicReader.java      # .litematic 文件解析
│   │   ├── LitematicWriter.java      # 过滤后文件写入
│   │   └── BlockFilter.java          # 方块筛选逻辑
│   ├── command/
│   │   └── StripCommand.java         # /stripper 命令注册
│   └── gui/
│       ├── StripperKeybind.java      # O 键绑定
│       ├── StripperScreen.java       # 可视化界面
│       └── StripperModMenu.java      # ModMenu 集成
└── src/main/resources/
    ├── fabric.mod.json               # 模组元数据
    └── assets/stripper/lang/         # 多语言支持
```
