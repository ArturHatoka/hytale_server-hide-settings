# HideEnemyHealth (Hytale Server Mod/Plugin)

A server-side mod/plugin for **Hytale** that hides **HP bars above entities** and (optionally) **damage/heal numbers**. It can be controlled **in-game via an admin UI dashboard** and also supports a disk-based config.

> Goal: reduce visual clutter in combat and keep the server’s visuals cleaner — without requiring a client mod.

---

## Features

- ✅ Hide **HP bars** above entities
- ✅ Hide **combat text** (damage/heal numbers)
- ✅ Separate settings for:
  - Players (`players`)
  - NPCs (`npcs`)
- ✅ In-game admin **Dashboard UI** (ON/OFF toggles)
- ✅ Saves settings to a file and applies them live (refresh)
- ✅ Server-side approach: modifies an entity’s `UIComponentList` → the client renders less UI

---

## Requirements

- Hytale Server (compatibility depends on the Server API)
- Java 25

---

## Installation (Server)

1. Build the `.jar` (see **Build** below) or use a prebuilt release.
2. Place the mod into your server folder (commonly `mods/` — depends on your setup).
3. Start the server.
4. Join with an account that has the `hideenemyhealth.admin` permission or get /op.
5. Open the dashboard:
   - `/hid ui`

After the first start, the config file will be created:
- `mods/HideEnemyHealth/config.json`

---

## Commands

Base command: `hid`

- `/hid help` — show help
- `/hid info` — show mod info and current settings
- `/hid ui` — open the admin dashboard
- `/hid reload` — reload config from disk (admin)

---

## Permissions

- `hideenemyhealth.admin` — access to the UI and admin functions

> Important: make sure your server’s permission system actually grants this permission to the correct role/player.

---

## Configuration

File: `mods/HideEnemyHealth/config.json`

Example:
```json
{
  "enabled": true,
  "players": {
    "hideHealthBar": true,
    "hideDamageNumbers": false
  },
  "npcs": {
    "hideHealthBar": true,
    "hideDamageNumbers": false
  }
}
