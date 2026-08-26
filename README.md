# Litematica Stripper - 投影剥离工具

一个基于 Fabric 的 Minecraft 模组，用于从 Litematica 投影文件中筛选指定建筑结构方块。

## 功能

- **保留模式**：只保留指定的方块，其余替换为空气
- **移除模式**：移除指定的方块，替换为空气
- **通配符支持**：`cherry_log`、`*_log`、`*_planks` 等模式匹配
- **多区域处理**：自动处理投影中的所有区域

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) (>= 0.16.0)
2. 将 `build/libs/[投影剥离] litematica-stripper-1.0.0+mc1.21.1.jar` 放入 `.minecraft/mods/` 目录
3. 同时安装 [Fabric API](https://modrinth.com/mod/fabric-api)

## 使用方法

### 游戏内命令

```
/stripper keep <投影文件.litematic> <方块ID或模式>
```
只保留指定方块（如樱花原木），其余全部替换为空气。

```
/stripper remove <投影文件.litematic> <方块ID或模式>
```
移除指定方块，其余保留。

### 示例

```bash
# 只保留樱花原木
/stripper keep 我的建筑.litematic cherry_log

# 移除所有树叶
/stripper remove 我的建筑.litematic *_leaves

# 保留多种方块（逗号分隔）
/stripper keep 我的建筑.litematic cherry_log,cherry_planks,white_concrete
```

### 文件位置

模组会按以下顺序查找文件：
1. `.minecraft/schematics/<文件名>`
2. `.minecraft/schematics/<文件名>.litematic`
3. 当前目录下直接路径

输出文件保存在与输入文件相同的目录中。

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
│   └── command/
│       └── StripCommand.java         # /stripper 命令注册
└── src/main/resources/
    ├── fabric.mod.json               # 模组元数据
    └── assets/stripper/lang/         # 多语言支持
```
