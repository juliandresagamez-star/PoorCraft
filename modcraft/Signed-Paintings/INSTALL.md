# Signed Paintings — Animated Media Fork

## Installation

### Requirements
- Minecraft 26.2 (Fabric)
- Fabric Loader 0.19.3+
- Fabric API 0.154.0+26.2
- Java 21+

### Installation Steps

1. Download `signed_paintings-1.3.0-mc26.2.jar` from the build output
2. Place the JAR in your `.minecraft/mods/` folder
3. Launch Minecraft with the Fabric loader

No additional dependencies need to be installed — the WebP decoder (TwelveMonkeys) is bundled inside the mod JAR.

### Compatibility
- **Sodium**: Compatible. The mod includes block rendering compatibility with Sodium since signs are block-model-rendered in MC 26.2.
- **Better Block Entities (BBE)**: Compatible. Custom BBE rendering integration is included.
- **Other mods**: The mod is client-side only and should not conflict with server-side mods.

### Configuration

The mod stores its configuration in `<game_dir>/config/signed_paintings.txt`. This file is created automatically on first run.

Settings can be adjusted via in-game commands:
- `/paintings:toggle signs|banners|shields` — Toggle rendering for specific block types
- `/paintings:toggle logging` — Toggle detailed logging
- `/paintings:reload` — Reload all images
- `/paintings:status` — Show loaded image information

## Usage

### Placing Images
1. Place a sign (wall sign, standing sign, or hanging sign)
2. Edit the sign and paste an HTTPS URL to an image
3. The image will be downloaded and displayed on the sign face

### Supported Formats
| Format | Static | Animated | Notes |
|--------|--------|----------|-------|
| PNG    | ✅     | —        | Standard static images |
| JPEG   | ✅     | —        | Standard static images |
| BMP    | ✅     | —        | Standard static images |
| GIF    | ✅     | ✅       | Full disposal method support |
| APNG   | ✅     | ✅       | Animated PNG via acTL/fcTL/fdAT chunks |
| WebP   | ✅     | ✅       | Via TwelveMonkeys ImageIO (VP8/VP8L) |

### Banners and Shields
Banners and shields can also display images by naming them with a URL using an anvil.

### URL Shortcuts
The mod supports URL aliases for common hosts:
- `imgur:XXXXX` → `https://i.imgur.com/XXXXX.png`
- `iili:XXXXX` → `https://iili.io/XXXXX.png`

Custom aliases can be configured in `signed_paintings.txt`.

## Security

### HTTPS-Only Policy
- All image URLs must use HTTPS. HTTP URLs are silently rejected.
- The manual "Trust Domain" approval system has been removed.
- All valid HTTPS domains are automatically trusted.

### SSRF Protection
- DNS resolution is validated before connecting
- Private IP ranges are blocked (10.x, 172.16-31.x, 192.168.x, 127.x, etc.)
- Link-local, CGNAT (100.64/10), and cloud metadata (169.254.169.254) addresses are blocked
- URLs with userinfo (`user:pass@host`) are rejected
- Maximum URL length: 2048 characters
- Maximum redirect depth: 3 hops (each hop is re-validated)

## Multiplayer

### How It Works
This mod is **client-side only**. No server-side installation is required.

Image URLs are stored in the sign's text content, which is part of the standard vanilla sign block entity data. When a sign is placed or edited, Minecraft's vanilla networking automatically synchronizes the sign text to all connected clients.

Any player who has this mod installed will:
1. See the sign text
2. Detect that it contains a URL
3. Download and display the image from that URL

Players **without** the mod will see the raw URL text on the sign.

### Requirements for Multiplayer
- Each client must have the mod installed independently
- Each client downloads images from the original URL (no server proxy)
- The image hosting server must be accessible from each client's network
- All clients share the same animation timing (based on `System.currentTimeMillis()`)

### No Server Component Needed
Since sign text is synced by vanilla Minecraft, no server-side mod, plugin, or configuration is needed. The mod works on:
- Vanilla servers
- Paper/Spigot servers
- Fabric servers (with or without the mod installed server-side)
- Realms
