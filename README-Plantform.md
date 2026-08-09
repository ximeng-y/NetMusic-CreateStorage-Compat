# NetMusic-CreateStorage-Compat

NetMusic 与 Create:Storage 的兼容修复 mod：让 NetMusic 的音乐唱片（`music_cd`）可以放入 Create:Storage 背包的唱片机升级（JukeboxUpgrade）槽位并正常播放，充当随身音响。

## 功能

- **NetMusic 唱片入槽**：唱片机升级槽位原本只认原版 `jukebox_playable` 组件，NetMusic 唱片会被拒绝放入；本 mod 放行 NetMusic 唱片
- **三种形态播放**：穿戴（WORN）、方块（BLOCK）、装置（CONTRAPTION）背包均可播放

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Create:Storage（fxntstorage） | 1.3.0+ |
| NetMusic | 1.5.1+ |
| Java | 21 |

## 安装

1. 安装 NeoForge 21.1.x 与前置 mod（Create:Storage、Create、NetMusic）
2. 下载成品 jar
3. 启动游戏即可使用

## 使用

### 基本用法

1. 在背包中放入唱片机升级（JukeboxUpgrade）
2. 将刻录好的 NetMusic 唱片（`music_cd`）放入唱片槽
3. 点击 ▶ 播放、⏹ 停止，扬声器按钮静音；换碟 / 取碟 / 移除升级均会正确停止播放

### 三种形态

- **穿戴（WORN）**：声音跟随玩家
- **方块（BLOCK）**：声音在背包方块处播放，附近玩家可闻
- **装置（CONTRAPTION）**：声音跟随装置移动

## 许可

本 mod 代码采用 MIT 许可

---

# NetMusic-CreateStorage-Compat (English)

A compatibility fix mod for NetMusic and Create:Storage: it allows NetMusic music discs (`music_cd`) to be placed into the JukeboxUpgrade slot of Create:Storage backpacks and play normally, turning the backpack into a portable music player.

## Features

- **NetMusic disc in slot**: The jukebox upgrade slot only accepted vanilla `jukebox_playable` components, rejecting NetMusic discs; this mod lets NetMusic discs in
- **Three backpack forms**: WORN, BLOCK and CONTRAPTION backpacks all support playback

## Requirements

| Item | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Create:Storage (fxntstorage) | 1.3.0+ |
| NetMusic | 1.5.1+ |
| Java | 21 |

## Installation

1. Install NeoForge 21.1.x and the prerequisite mods (Create:Storage, Create, NetMusic)
2. Download the release jar
3. Launch the game and it just works

## Usage

### Basic usage

1. Put a JukeboxUpgrade into a backpack
2. Put a burned NetMusic disc (`music_cd`) into the disc slot
3. Click ▶ to play, ⏹ to stop, and the speaker button to mute; swapping the disc, removing the disc or removing the upgrade all stop playback correctly

### The three forms

- **WORN**: the sound follows the player
- **BLOCK**: the sound plays at the backpack block, audible to nearby players
- **CONTRAPTION**: the sound follows the contraption

## License

This mod's code is licensed under MIT
