![Logo](https://cdn.modrinth.com/data/cached_images/e216316190e9c7aa39c280cf8a2bd6adb1f51fe0.png)


---

A complete rewrite of the Minecraft chat experience. Send media, reply to messages, use context menus, render items and entities in chat, and customize everything seamlessly.


[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/chat-remastered)

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/chat-remastered?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/chat-remastered)
[![Game Versions](https://img.shields.io/modrinth/game-versions/chat-remastered?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/chat-remastered)

[![Fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg)](https://fabricmc.net/)
[![Paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg)](https://papermc.io/)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/spigot_vector.svg)](https://www.spigotmc.org/)
[![NeoForge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/unsupported/neoforge_vector.svg)](https://neoforged.net/)

[![Fabric API](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg)](https://modrinth.com/mod/fabric-api)
[![Cloth Config API](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/cloth-config-api_vector.svg)](https://modrinth.com/mod/cloth-config)

---

## ⚡ Features

- **Instant Inline Rendering** — images and animated GIFs render directly in the chat window.
- **Multi-file Sharing** — send up to **15 photos at once** in a single message.
- **Wide Format Support** — PNG, JPEG, WebP, BMP, TIFF, animated GIF.
- **Screenshots Panel** — a dedicated button in the chat input field opens a panel with your local screenshots for quick selection and sending, no need to dig through folders.
- **Item & Entity Rendering** — render items, entities, mobs, or other players directly into chat via special chat tags (see Commands below).
- **Replies & Threads** — reply to any text message or photo with a visual connection, just like Discord.
- **Context Menu (Right-Click)** — reply, copy text, or manage messages via a modern pop-up menu.
- **Text Selection** — highlight and copy specific lines directly in the chat window.
- **In-game Settings Menu** — adjust image scaling (50%–200%), closed-chat line limits, and chat window height without leaving the game.
- **Fullscreen Viewer** — click any image to zoom in.
- **Animated Chat Cards** — new messages and media cards smoothly spawn in, and removed/deleted cards animate out instead of just vanishing.
- **Cooldown System** — prevents chat/media spam.
- **Server Moderation** — granular photo/reply blocking, global media deletion, integration with server mute mods.
- **Asynchronous TCP Transfer** — media runs on its own dedicated port (`5050`), separate from the game loop.

![1](https://cdn.modrinth.com/data/cached_images/0e05e9574401b126128694a275effd42831f0028.png)

---

## 📷 How to Send Media

- 🖱 Drag & Drop right into the chat window
- 📁 Click the attachment icon to open the file picker
- 📋 Paste from clipboard with `Ctrl + V`
- 🖼 Click the screenshots icon in the chat input to pick from recent screenshots
- 📦 Up to 15 images per message

![2](https://cdn.modrinth.com/data/cached_images/175a34aadb290e79dcc7d6913810e8a588f40ca0.png)

---

## 📋 Requirements

- Fabric Loader (latest recommended)
- Fabric API
- Cloth Config API

---

## 🛠 Installation

**Client-Side (Required)**
1. Install Fabric Loader
2. Place **Fabric API** into your mods folder
3. Place **Cloth Config API** into your mods folder
4. Place `chat-remastered.jar` into `.minecraft/mods/`

**Server-Side (Required for sharing)**
1. Place the mod and its dependencies into the server's mods folder
2. Open TCP port `5050` (or change it in config) to allow media transfer
3. Configure settings via `config/chat-remastered-server.json`

> ⚠️ Without the server-side installation, players will not be able to send or view media.

---

## 🔌 Supported Mods & Plugins

Chat Remastered automatically detects these mods/plugins if installed and hooks into them out of the box — no extra configuration needed.

| Mod/Plugin | Integration |
|---|---|
| 🔨 [BanHammer](https://modrinth.com/mod/banhammer) | Muted players are automatically blocked from sending images, GIFs, and other media attachments. |
| ✈️ [vanutp's Telegram Bridge - Minecraft Plugin](https://modrinth.com/plugin/tgbridge) | Two-way reply linking between Minecraft and Telegram: replies sent in-game are relayed as Telegram replies and vice versa. Photos and grouped photo sends are mirrored as Telegram messages, and deleting an image in-game also removes the corresponding message in Telegram. |

---

## ⚙️ Server Configuration

| Option | Default | Description |
|---|---|---|
| `resolution` | `720` | Max media resolution (360 / 480 / 720 / HD / 2K) |
| `imagePort` | `5050` | TCP transfer port |
| `photoCooldownSeconds` | `5` | Cooldown between uploads per player (seconds) |
| `gifEnabled` | `true` | Allow players to send animated GIFs |
| `gifMaxDim` | `480` | Max GIF resolution players can upload (240–1920) |
| `maxPhotosPerMessage` | `5` | Max number of photos a player can attach to a single message (server-enforced, up to 15) |
| `mutedMessage` | *(custom text)* | Message shown to a muted player when they try to send text, photos, or replies |

---

## 🔧 Commands

<details>
<summary><b>Click to expand full command list</b></summary>

### 🖥 Server Commands (`/chat-remastered-admin`) — operator/console only

| Command | Description |
|---|---|
| `/chat-remastered-admin block-photo <player>` | Block player from sending photos |
| `/chat-remastered-admin unblock-photo <player>` | Unblock player from sending photos (no rejoin needed) |
| `/chat-remastered-admin mute <player>` | Block player from sending photos and replies |
| `/chat-remastered-admin unmute <player>` | Unblock player from sending photos and replies |
| `/chat-remastered-admin delete <imageId>` | Delete image for all players globally |
| `/chat-remastered-admin test <player>` | Check mod status, ban status and upload token for a player |
| `/chat-remastered` | Show mod version and protocol info |

### 💻 Client Commands (`/chat-remastered`) — client-side only

| Command | Description |
|---|---|
| `/chat-remastered clearcache` | Clear image disk cache (RAM cache stays) |
| `/chat-remastered delete <imageId>` | Delete image (forwarded to server) |
| `/chat-remastered` | Show mod version and protocol info |

### 🎨 Rich Chat Tags — item & entity rendering

Type these directly into the chat input to render items, entities, or players inline instead of plain text. Optional caption text goes after the closing `>`.

| Tag | Renders |
|---|---|
| `<item>your text` | The item currently in your main hand |
| `<entity>your text` | The entity you're currently looking at (crosshair target) |
| `<player:PlayerName>your text` | A preview of the given player |
| `<chat_remastered:item:namespace:path>your text` | A specific item by ID, e.g. `<chat_remastered:item:minecraft:diamond_sword>` |
| `<chat_remastered:item:namespace:path{nbt}>your text` | A specific item by ID with NBT/component data |
| `<chat_remastered:entity:namespace:path:tocursor>your text` | A specific entity by ID, positioned toward your cursor |
| `<chat_remastered:entity:namespace:path:rotate>your text` | A specific entity by ID, auto-rotating in place |
| `<chat_remastered:entity:namespace:path{nbt}:rotate:offsetX:offsetY:size>your text` | Entity with NBT, custom offset and render size |
| `<chat_remastered:player:PlayerName:tocursor>your text` | A player render positioned toward your cursor |
| `<chat_remastered:player:PlayerName:rotate>your text` | A player render, auto-rotating in place |
| `<uuid-here>your text` | A player render by UUID |

> Players without the mod installed will see a plain-text fallback (e.g. `[item:minecraft:diamond_sword]`) instead of the rendered preview.

### 🛠 Debug Commands (`/chatremastereddebug`) — for developers/testing

| Command | Description |
|---|---|
| `/chatremastereddebug placeholder [ratio]` | Show image placeholder with given aspect ratio |
| `/chatremastereddebug placeholder custom <width> <height>` | Show placeholder with custom size |
| `/chatremastereddebug placeholder_deleted [ratio]` | Show deleted image placeholder (red ✗) |
| `/chatremastereddebug placeholder_error [ratio]` | Show error image placeholder (yellow !) |
| `/chatremastereddebug test` | Test connection to mod server |

💡 Available aspect ratios: `1_1`, `4_3`, `3_2`, `16_9`, `16_10`, `21_9`, `9_16`, `3_4`, `2_3`

> Tip: Right-click any media or message in chat to quickly copy its ID for deletion or management.

</details>


---

## ❓ FAQ

**Do all players need this mod installed?**
Yes, both the server and the clients must have the mod installed to see and send images.

**Does this mod upload images to third-party hosting?**
No, it uses its own lightweight built-in TCP transfer system directly through your server, keeping your data private.

**Will big images lag the server or cause TPS drops?**
No. Media transfer runs fully asynchronously on a dedicated TCP port (5050). Traffic peaks at just a few MB/s, and RAM usage is capped at ~100MB.

**How is media stored? Is there an auto-wipe?**
Images are stored in the server cache as binary files with sender data. To save disk space, they are automatically deleted after 1 day. If an admin or player deletes a message, it is instantly wiped from both server and client caches.

**How does the mute system work?**
You can completely mute a player (blocking photos and replies) or just restrict them from sending photos. It also has native integration with BanHammer — any active mute there automatically applies to Chat Remastered.

**Will shaders (Iris/Oculus) break the image rendering?**
No. The chat media render operates as an independent UI element and does not interfere with the 3D world rendering or shader pipelines.

**What happens if a file is corrupted or a packet is lost?**
The mod doesn't rely on external URLs. If something goes wrong during transmission or rendering, the image is instantly replaced with a clean error placeholder (yellow !).

**Are sent images logged in the server console?**
Not currently, as the server console only handles text data. Advanced logging features for administrators are planned for future updates.

---

## 📅 Road Map

**Supported Versions:** Fabric 1.21.11 ✔

| Platform / Feature | Status |
|---|---|
| Fabric (26.x+) | 🔜 In progress |
| NeoForge 1.21.11 - 26.x | 🔜 In progress |
| Fabric legacy (1.20.x) | 📅 Planned |
| Forge / NeoForge | 📅 Planned |
| Quilt | 📅 Planned |
| Paper / Spigot / Purpur (Server plugin) | ⚡ Planned |
| Video support | ❌ Cancelled (highly unlikely due to heavy Minecraft performance impact) |
