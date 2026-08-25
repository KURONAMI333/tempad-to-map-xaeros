# Tempad to Map: Xaero's edition

> See every Tempad teleport location on Xaero's Minimap without copying coordinates by hand.

Tempad keeps your saved teleport locations in one place, while Xaero's
Minimap is where you navigate. This addon sends each player's own Tempad
locations from the server and keeps matching waypoints up to date.

- Adds Tempad locations to Xaero's Minimap as persistent waypoints.
- Syncs additions, changes, and removals automatically.
- Works in single-player, LAN, and dedicated-server worlds.
- Sends only each player's own locations.

## Supported loaders / versions

| Minecraft | NeoForge |
|---|:---:|
| 1.21.1 | ✅ |

Install this mod and Tempad on both the client and server. Xaero's Minimap is
needed only on clients that should display the waypoints.

## Install

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Install [Tempad 3.x](https://modrinth.com/mod/tempad) on the client and server.
3. Install [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) on the client.
4. Put `tempadtomapx-0.1.0.jar` in the `mods` folder on each required side.

## Dependencies

- Required: [Tempad](https://modrinth.com/mod/tempad) 3.x on the client and server.
- Optional client display: [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap).

## Usage

Create, edit, or delete a location in Tempad. The corresponding Xaero's
Minimap waypoint is added, updated, or removed automatically within a few
seconds.

## Scope & limitations

This is one-way synchronization from Tempad to Xaero's Minimap. Editing a
waypoint in Xaero's Minimap does not change Tempad. Xaero's Minimap can expose
only the current dimension's waypoint set, so locations in other dimensions
are applied when the player enters them.

## License & credits

All Rights Reserved. Free to put in any modpack, on any platform, monetised or
not - no permission needed, no credit required. Source is published so you can
read exactly what it does.

Author: KURONAMI
