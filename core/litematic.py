"""Litematic (.litematic) file reader and writer.

Handles parsing Litematica schematic files including block state
palette and packed block data. Supports filtering blocks by type.
"""

import gzip
import math
import struct

from .nbt import parse as nbt_parse
from .nbt import write as nbt_write
from .nbt import unsigned_to_signed_64
from .nbt import signed_to_unsigned_64
from .nbt import TAG_INT, TAG_LONG, TAG_COMPOUND, TAG_STRING, TAG_LIST


class Region:
    """Represents a single region within a Litematic file."""

    def __init__(self, name, position=None, size=None):
        self.name = name
        self.position = position or {"x": 0, "y": 0, "z": 0}
        self.size = size or {"x": 0, "y": 0, "z": 0}
        self.palette = []          # List of {"Name": str, "Properties": dict|None}
        self.block_indices = []    # Unpacked palette indices for each position
        self.entities = []
        self.tile_entities = []
        self.pending_block_ticks = []
        self.pending_fluid_ticks = []

    @property
    def volume(self):
        return abs(self.size["x"] * self.size["y"] * self.size["z"])


class LitematicFile:
    """Represents a complete Litematica schematic file."""

    def __init__(self):
        self.version = 0
        self.minecraft_data_version = 0
        self.metadata = {}
        self.regions = {}          # {name: Region}
        self._raw_root = None      # Keep original NBT for passthrough fields

    @classmethod
    def load(cls, filepath):
        """Load a .litematic file from disk."""
        with open(filepath, "rb") as f:
            raw = f.read()
        return cls.from_bytes(raw)

    @classmethod
    def from_bytes(cls, data):
        """Parse a .litematic file from raw bytes (gzip-compressed NBT)."""
        try:
            decompressed = gzip.decompress(data)
        except OSError:
            decompressed = data

        root = nbt_parse(decompressed)
        obj = cls()
        obj._raw_root = root

        # Root is typically named "" with a compound value
        root_name, root_data = next(iter(root.items()))

        obj.version = root_data.get("Version", 5)
        obj.minecraft_data_version = root_data.get("MinecraftDataVersion", 0)
        obj.metadata = root_data.get("Metadata", {})

        regions_raw = root_data.get("Regions", {})
        for region_name, region_data in regions_raw.items():
            region = cls._parse_region(region_name, region_data)
            obj.regions[region_name] = region

        return obj

    @classmethod
    def _parse_region(cls, name, data):
        region = Region(name)

        pos = data.get("Position", {})
        region.position = {
            "x": pos.get("x", 0),
            "y": pos.get("y", 0),
            "z": pos.get("z", 0),
        }

        size = data.get("Size", {})
        region.size = {
            "x": size.get("x", 0),
            "y": size.get("y", 0),
            "z": size.get("z", 0),
        }

        # Parse block state palette
        palette_raw = data.get("BlockStatePalette", [])
        for entry in palette_raw:
            palette_entry = {
                "Name": entry.get("Name", "minecraft:air"),
                "Properties": entry.get("Properties"),
            }
            region.palette.append(palette_entry)

        # Unpack block states
        volume = region.volume
        if volume > 0:
            longs_raw = data.get("BlockStates", [])
            if longs_raw and len(region.palette) > 1:
                bits = max(2, math.ceil(math.log2(len(region.palette))))
                region.block_indices = unpack_block_states(longs_raw, bits, volume)
            elif longs_raw:
                bits = max(2, math.ceil(math.log2(max(1, len(region.palette)))))
                region.block_indices = [0] * volume
            else:
                region.block_indices = [0] * volume

        region.entities = data.get("Entities", [])
        region.tile_entities = data.get("TileEntities", [])
        region.pending_block_ticks = data.get("PendingBlockTicks", [])
        region.pending_fluid_ticks = data.get("PendingFluidTicks", [])

        return region

    def get_all_block_types(self):
        """Return a sorted list of unique block names across all regions."""
        names = set()
        for region in self.regions.values():
            for entry in region.palette:
                names.add(entry["Name"])
        return sorted(names)

    def count_blocks(self, block_name):
        """Count occurrences of a specific block across all regions."""
        count = 0
        for region in self.regions.values():
            for i, idx in enumerate(region.block_indices):
                if idx < len(region.palette) and region.palette[idx]["Name"] == block_name:
                    count += 1
        return count

    def get_statistics(self):
        """Get block statistics for all regions."""
        stats = {}
        for region in self.regions.values():
            counts = {}
            for idx in region.block_indices:
                if idx < len(region.palette):
                    name = region.palette[idx]["Name"]
                    counts[name] = counts.get(name, 0) + 1
            stats[region.name] = counts
        return stats

    def filter_blocks(self, keep_blocks=None, remove_blocks=None,
                      replacement="minecraft:air"):
        """Filter blocks by keeping or removing specific types.

        Args:
            keep_blocks: Set of block names to keep (others replaced).
                         If None, all are kept.
            remove_blocks: Set of block names to remove (replaced by `replacement`).
                           If None, none are removed.
            replacement: Block name to use for removed positions.

        Returns:
            A new LitematicFile with filtered content.
        """
        result = LitematicFile()
        result.version = self.version
        result.minecraft_data_version = self.minecraft_data_version
        result.metadata = dict(self.metadata)

        for region_name, region in self.regions.items():
            new_region = Region(region_name)
            new_region.position = dict(region.position)
            new_region.size = dict(region.size)

            # Determine which palette entries to keep
            old_palette = region.palette
            index_mapping = {}   # old_index -> new_index
            new_palette = []
            needs_air = False

            # First pass: determine which old entries map to which new entries
            air_new_idx = None
            for old_idx, entry in enumerate(old_palette):
                name = entry["Name"]
                should_remove = False

                if remove_blocks is not None and name in remove_blocks:
                    should_remove = True
                elif keep_blocks is not None and name not in keep_blocks:
                    should_remove = True

                if should_remove:
                    needs_air = True
                    # Map to air's new index later
                else:
                    new_idx = len(new_palette)
                    index_mapping[old_idx] = new_idx
                    new_palette.append(entry)

            # Add air to new palette if needed for removed blocks
            if needs_air:
                # Check if air is already in new_palette
                air_found = False
                for i, entry in enumerate(new_palette):
                    if entry["Name"] == replacement:
                        air_found = True
                        break
                if not air_found:
                    new_palette.append({"Name": replacement, "Properties": None})

                # Find replacement index
                replace_idx = None
                for i, entry in enumerate(new_palette):
                    if entry["Name"] == replacement:
                        replace_idx = i
                        break

                # Now fill in mapping for removed indices
                for old_idx, entry in enumerate(old_palette):
                    name = entry["Name"]
                    should_remove = False
                    if remove_blocks is not None and name in remove_blocks:
                        should_remove = True
                    elif keep_blocks is not None and name not in keep_blocks:
                        should_remove = True
                    if should_remove:
                        index_mapping[old_idx] = replace_idx

            # Ensure at least one palette entry
            if len(new_palette) == 0:
                new_palette.append({"Name": "minecraft:air", "Properties": None})

            new_region.palette = new_palette

            # Remap block indices
            new_indices = []
            for old_idx in region.block_indices:
                if old_idx in index_mapping:
                    new_indices.append(index_mapping[old_idx])
                else:
                    # Fallback: find in new palette or default to 0
                    new_indices.append(0)
            new_region.block_indices = new_indices

            new_region.entities = region.entities
            new_region.tile_entities = region.tile_entities
            new_region.pending_block_ticks = region.pending_block_ticks
            new_region.pending_fluid_ticks = region.pending_fluid_ticks

            result.regions[region_name] = new_region

        # Update metadata
        total_blocks = sum(
            sum(1 for i in r.block_indices if r.palette[i]["Name"] != "minecraft:air")
            for r in result.regions.values()
        )
        total_volume = sum(r.volume for r in result.regions.values())

        result.metadata = dict(self.metadata)
        result.metadata["TotalBlocks"] = total_blocks
        result.metadata["TotalVolume"] = total_volume
        result.metadata["RegionCount"] = len(result.regions)

        return result

    def save(self, filepath):
        """Save to a .litematic file."""
        raw = self.to_bytes()
        with open(filepath, "wb") as f:
            f.write(raw)

    def to_bytes(self):
        """Serialize to gzip-compressed NBT bytes."""
        root_data = {
            "Version": self.version,
            "MinecraftDataVersion": self.minecraft_data_version,
            "SubVersion": self.metadata.pop("SubVersion", 1),
            "Metadata": self._build_metadata_nbt(),
            "Regions": self._build_regions_nbt(),
        }

        root = {"": root_data}
        nbt_bytes = nbt_write(root)
        return gzip.compress(nbt_bytes)

    def _build_metadata_nbt(self):
        meta = dict(self.metadata)

        # Build EnclosingSize
        min_x = min_y = min_z = float('inf')
        max_x = max_y = max_z = float('-inf')
        for region in self.regions.values():
            rx, ry, rz = region.position["x"], region.position["y"], region.position["z"]
            sx, sy, sz = abs(region.size["x"]), abs(region.size["y"]), abs(region.size["z"])
            min_x = min(min_x, rx)
            min_y = min(min_y, ry)
            min_z = min(min_z, rz)
            max_x = max(max_x, rx + sx)
            max_y = max(max_y, ry + sy)
            max_z = max(max_z, rz + sz)

        if self.regions:
            meta["EnclosingSize"] = {
                "x": int(max_x - min_x),
                "y": int(max_y - min_y),
                "z": int(max_z - min_z),
            }
        else:
            meta["EnclosingSize"] = {"x": 0, "y": 0, "z": 0}

        return meta

    def _build_regions_nbt(self):
        regions = {}
        for name, region in self.regions.items():
            region_data = {
                "Position": region.position,
                "Size": region.size,
                "BlockStatePalette": [
                    self._palette_entry_to_nbt(entry) for entry in region.palette
                ],
                "BlockStates": self._pack_block_states(region),
                "Entities": region.entities,
                "TileEntities": region.tile_entities,
                "PendingBlockTicks": region.pending_block_ticks,
                "PendingFluidTicks": region.pending_fluid_ticks,
            }
            regions[name] = region_data
        return regions

    def _palette_entry_to_nbt(self, entry):
        result = {"Name": entry["Name"]}
        if entry.get("Properties"):
            props = {}
            for k, v in entry["Properties"].items():
                props[k] = str(v)
            result["Properties"] = props
        return result

    def _pack_block_states(self, region):
        if not region.block_indices:
            return []

        palette_size = max(1, len(region.palette))
        bits = max(2, math.ceil(math.log2(palette_size)))

        return pack_block_states(region.block_indices, bits)


def unpack_block_states(longs, bits_per_entry, count):
    """Unpack block state indices from a list of signed longs.

    Uses non-spanning bit packing (like Minecraft 1.16+), where each
    entry fits entirely within one long.
    """
    values = []
    mask = (1 << bits_per_entry) - 1
    entries_per_long = 64 // bits_per_entry

    for i in range(count):
        long_index = i // entries_per_long
        if long_index >= len(longs):
            values.append(0)
            continue
        offset = (i % entries_per_long) * bits_per_entry
        unsigned = signed_to_unsigned_64(longs[long_index])
        val = (unsigned >> offset) & mask
        values.append(val)

    return values


def pack_block_states(values, bits_per_entry):
    """Pack block state indices into signed longs using non-spanning layout."""
    entries_per_long = 64 // bits_per_entry
    num_longs = math.ceil(len(values) / entries_per_long) if entries_per_long > 0 else 0
    mask = (1 << bits_per_entry) - 1

    result = [0] * max(num_longs, 0)

    for i, val in enumerate(values):
        long_index = i // entries_per_long
        offset = (i % entries_per_long) * bits_per_entry
        result[long_index] |= (val & mask) << offset

    # Convert to signed
    return [unsigned_to_signed_64(v) for v in result]
