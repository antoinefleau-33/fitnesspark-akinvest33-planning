#!/usr/bin/env python3
"""
Generateur de schematics pour la ferme a petales roses (DonutSMP).

Produit trois fichiers a partir d'une seule definition de la structure :
  - pink_petal_farm.litematic  (Litematica, schema v5)
  - pink_petal_farm.schem      (Sponge Schematic v2, WorldEdit / FAWE)
  - pink_petal_farm.nbt        (structure vanilla, bloc de structure)

Aucune dependance externe : l'ecriture NBT est faite a la main.
Lancer :  python3 generate_farm.py
"""

import gzip
import io
import os
import struct
from collections import Counter

# --------------------------------------------------------------------------
# Ecriture NBT minimale
# --------------------------------------------------------------------------

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG = 0, 1, 2, 3, 4
TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING = 5, 6, 7, 8
TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 9, 10, 11, 12


class Tag:
    def __init__(self, tid, value):
        self.tid = tid
        self.value = value


def TByte(v):
    return Tag(TAG_BYTE, v)


def TShort(v):
    return Tag(TAG_SHORT, v)


def TInt(v):
    return Tag(TAG_INT, v)


def TLong(v):
    return Tag(TAG_LONG, v)


def TString(v):
    return Tag(TAG_STRING, v)


def TByteArray(v):
    return Tag(TAG_BYTE_ARRAY, v)


def TIntArray(v):
    return Tag(TAG_INT_ARRAY, v)


def TLongArray(v):
    return Tag(TAG_LONG_ARRAY, v)


def TList(item_tid, items):
    return Tag(TAG_LIST, (item_tid, items))


def TCompound(d):
    return Tag(TAG_COMPOUND, d)


def _w_string(out, s):
    data = s.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _w_payload(out, tag):
    tid, v = tag.tid, tag.value
    if tid == TAG_BYTE:
        out.write(struct.pack(">b", v))
    elif tid == TAG_SHORT:
        out.write(struct.pack(">h", v))
    elif tid == TAG_INT:
        out.write(struct.pack(">i", v))
    elif tid == TAG_LONG:
        out.write(struct.pack(">q", v))
    elif tid == TAG_FLOAT:
        out.write(struct.pack(">f", v))
    elif tid == TAG_DOUBLE:
        out.write(struct.pack(">d", v))
    elif tid == TAG_BYTE_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(bytes((b & 0xFF) for b in v))
    elif tid == TAG_STRING:
        _w_string(out, v)
    elif tid == TAG_LIST:
        item_tid, items = v
        out.write(struct.pack(">b", item_tid if items else TAG_END))
        out.write(struct.pack(">i", len(items)))
        for it in items:
            _w_payload(out, it)
    elif tid == TAG_COMPOUND:
        for name, sub in v.items():
            out.write(struct.pack(">b", sub.tid))
            _w_string(out, name)
            _w_payload(out, sub)
        out.write(struct.pack(">b", TAG_END))
    elif tid == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(v)))
        for i in v:
            out.write(struct.pack(">i", i))
    elif tid == TAG_LONG_ARRAY:
        out.write(struct.pack(">i", len(v)))
        for i in v:
            out.write(struct.pack(">q", i))
    else:
        raise ValueError("tag inconnu: %d" % tid)


def write_nbt_file(path, root_name, root_tag):
    buf = io.BytesIO()
    buf.write(struct.pack(">b", root_tag.tid))
    _w_string(buf, root_name)
    _w_payload(buf, root_tag)
    with gzip.GzipFile(path, "wb", mtime=0) as f:
        f.write(buf.getvalue())


# --------------------------------------------------------------------------
# Definition de la structure
# --------------------------------------------------------------------------

SX, SY, SZ = 6, 7, 18          # largeur (X), hauteur (Y), longueur (Z)
ROWS = list(range(5, 17))      # rangees productives : z = 5..16
DATA_VERSION = 3955            # 1.21.1

AIR = ("minecraft:air", None)

world = [[[AIR for _ in range(SX)] for _ in range(SZ)] for _ in range(SY)]


def setb(x, y, z, name, props=None):
    world[y][z][x] = (name, props)


def fill_layer(y, name, props=None):
    for z in range(SZ):
        for x in range(SX):
            setb(x, y, z, name, props)


# --- Y0 : fondation + coffres de recolte -----------------------------------
fill_layer(0, "minecraft:stone")
setb(2, 0, 17, "minecraft:chest", {"facing": "south"})
setb(3, 0, 17, "minecraft:chest", {"facing": "south"})

# --- Y1 : fondation des rails, blocs de redstone, entonnoirs de vidange ----
fill_layer(1, "minecraft:stone")
for z in (6, 12):
    setb(2, 1, z, "minecraft:redstone_block")
    setb(3, 1, z, "minecraft:redstone_block")
setb(2, 1, 17, "minecraft:hopper", {"facing": "down"})
setb(3, 1, 17, "minecraft:hopper", {"facing": "down"})

# --- Y2 : boucle de rails (le wagon-entonnoir tourne dessous) --------------
fill_layer(2, "minecraft:stone")
for z in range(SZ):
    for x in (2, 3):
        if z == 0:
            shape = "south_east" if x == 2 else "south_west"
            setb(x, 2, z, "minecraft:rail", {"shape": shape})
        elif z == SZ - 1:
            shape = "north_east" if x == 2 else "north_west"
            setb(x, 2, z, "minecraft:rail", {"shape": shape})
        elif z in (6, 12):
            setb(x, 2, z, "minecraft:powered_rail",
                 {"shape": "north_south", "powered": "true"})
        else:
            setb(x, 2, z, "minecraft:rail", {"shape": "north_south"})

# --- Y3 : sol (mousse sous les petales) ------------------------------------
fill_layer(3, "minecraft:stone")
for z in range(SZ):
    for x in (2, 3):
        setb(x, 3, z, "minecraft:moss_block")

# --- Y4 : distributeurs, petales, entonnoirs d'alimentation ----------------
fill_layer(4, "minecraft:stone")
for z in ROWS:
    setb(0, 4, z, "minecraft:hopper", {"facing": "east"})
    setb(1, 4, z, "minecraft:dispenser", {"facing": "east"})
    setb(2, 4, z, "minecraft:pink_petals")
    setb(3, 4, z, "minecraft:pink_petals")
    setb(4, 4, z, "minecraft:dispenser", {"facing": "west"})
    setb(5, 4, z, "minecraft:hopper", {"facing": "west"})

# --- Y5 : horloge, bus de redstone, lignes de poudre, chaines d'entonnoirs -
# Horloge a entonnoirs (les objets font l'aller-retour entre les deux).
setb(2, 5, 0, "minecraft:hopper", {"facing": "east"})
setb(3, 5, 0, "minecraft:hopper", {"facing": "west"})
# Comparateur : "facing" designe l'ARRIERE (l'entree) -> il lit l'entonnoir au nord.
setb(2, 5, 1, "minecraft:comparator", {"facing": "north"})
# Repeteur d'amplification : sortie a 15 quel que soit le signal du comparateur.
setb(2, 5, 2, "minecraft:repeater", {"facing": "north", "delay": "4"})
# Bus transversal
for x in (1, 2, 3, 4):
    setb(x, 5, 3, "minecraft:redstone_wire")
# Repeteurs d'entree des deux lignes de distributeurs
setb(1, 5, 4, "minecraft:repeater", {"facing": "north", "delay": "1"})
setb(4, 5, 4, "minecraft:repeater", {"facing": "north", "delay": "1"})
for z in ROWS:
    setb(0, 5, z, "minecraft:hopper", {"facing": "south"})   # chaine farine d'os
    setb(1, 5, z, "minecraft:redstone_wire")                 # alimente les distributeurs
    setb(2, 5, z, "minecraft:stone")                         # anti-spawn au-dessus des petales
    setb(3, 5, z, "minecraft:stone")
    setb(4, 5, z, "minecraft:redstone_wire")
    setb(5, 5, z, "minecraft:hopper", {"facing": "south"})

# --- Y6 : toit + coffres d'approvisionnement en farine d'os ----------------
fill_layer(6, "minecraft:stone")
setb(0, 6, 5, "minecraft:chest", {"facing": "west"})
setb(0, 6, 6, "minecraft:chest", {"facing": "west"})
setb(5, 6, 5, "minecraft:chest", {"facing": "east"})
setb(5, 6, 6, "minecraft:chest", {"facing": "east"})
setb(2, 6, 0, *AIR)   # acces a l'horloge
setb(3, 6, 0, *AIR)


# --------------------------------------------------------------------------
# Palette
# --------------------------------------------------------------------------

def state_key(name, props):
    if not props:
        return name
    inner = ",".join("%s=%s" % (k, props[k]) for k in sorted(props))
    return "%s[%s]" % (name, inner)


palette = {}
palette_order = []


def pal_index(block):
    key = state_key(*block)
    if key not in palette:
        palette[key] = len(palette_order)
        palette_order.append(block)
    return palette[key]


# minecraft:air doit etre l'index 0 (convention Sponge)
pal_index(AIR)
for y in range(SY):
    for z in range(SZ):
        for x in range(SX):
            pal_index(world[y][z][x])


def pal_compound(block):
    name, props = block
    d = {"Name": TString(name)}
    if props:
        d["Properties"] = TCompound({k: TString(v) for k, v in props.items()})
    return TCompound(d)


# --------------------------------------------------------------------------
# Export .schem (Sponge Schematic v2)
# --------------------------------------------------------------------------

def write_varint(out, value):
    while True:
        b = value & 0x7F
        value >>= 7
        if value:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def export_schem(path):
    data = bytearray()
    for y in range(SY):
        for z in range(SZ):
            for x in range(SX):
                write_varint(data, pal_index(world[y][z][x]))

    schem = TCompound({
        "Version": TInt(2),
        "DataVersion": TInt(DATA_VERSION),
        "Width": TShort(SX),
        "Height": TShort(SY),
        "Length": TShort(SZ),
        "Offset": TIntArray([0, 0, 0]),
        "PaletteMax": TInt(len(palette_order)),
        "Palette": TCompound({state_key(*b): TInt(i)
                              for i, b in enumerate(palette_order)}),
        "BlockData": TByteArray(list(data)),
    })
    write_nbt_file(path, "Schematic", schem)


# --------------------------------------------------------------------------
# Export .nbt (structure vanilla)
# --------------------------------------------------------------------------

def export_structure(path):
    blocks = []
    for y in range(SY):
        for z in range(SZ):
            for x in range(SX):
                blocks.append(TCompound({
                    "pos": TList(TAG_INT, [TInt(x), TInt(y), TInt(z)]),
                    "state": TInt(pal_index(world[y][z][x])),
                }))

    root = TCompound({
        "DataVersion": TInt(DATA_VERSION),
        "size": TList(TAG_INT, [TInt(SX), TInt(SY), TInt(SZ)]),
        "palette": TList(TAG_COMPOUND, [pal_compound(b) for b in palette_order]),
        "blocks": TList(TAG_COMPOUND, blocks),
        "entities": TList(TAG_COMPOUND, []),
    })
    write_nbt_file(path, "", root)


# --------------------------------------------------------------------------
# Export .litematic (Litematica, schema v5)
# --------------------------------------------------------------------------

def pack_bits(indices, bits):
    """Tableau de bits facon Litematica : les valeurs peuvent chevaucher deux longs."""
    total = len(indices) * bits
    longs = [0] * ((total + 63) // 64)
    mask = (1 << bits) - 1
    for i, value in enumerate(indices):
        start = i * bits
        s_idx = start >> 6
        e_idx = ((i + 1) * bits - 1) >> 6
        s_off = start & 63
        longs[s_idx] = (longs[s_idx] & ~(mask << s_off) | ((value & mask) << s_off)) & 0xFFFFFFFFFFFFFFFF
        if s_idx != e_idx:
            end_off = 64 - s_off
            j = bits - end_off
            longs[e_idx] = ((longs[e_idx] >> j) << j | ((value & mask) >> end_off)) & 0xFFFFFFFFFFFFFFFF
    # conversion en entiers signes 64 bits
    return [(v - (1 << 64)) if v >= (1 << 63) else v for v in longs]


def export_litematic(path, name, author, description):
    indices = [0] * (SX * SY * SZ)
    for y in range(SY):
        for z in range(SZ):
            for x in range(SX):
                indices[y * SX * SZ + z * SX + x] = pal_index(world[y][z][x])

    bits = max(2, max(1, len(palette_order) - 1).bit_length())
    if len(palette_order) == 1:
        bits = 2
    non_air = sum(1 for i in indices if palette_order[i][0] != "minecraft:air")

    region = TCompound({
        "Position": TCompound({"x": TInt(0), "y": TInt(0), "z": TInt(0)}),
        "Size": TCompound({"x": TInt(SX), "y": TInt(SY), "z": TInt(SZ)}),
        "BlockStatePalette": TList(TAG_COMPOUND, [pal_compound(b) for b in palette_order]),
        "BlockStates": TLongArray(pack_bits(indices, bits)),
        "TileEntities": TList(TAG_COMPOUND, []),
        "Entities": TList(TAG_COMPOUND, []),
        "PendingBlockTicks": TList(TAG_COMPOUND, []),
        "PendingFluidTicks": TList(TAG_COMPOUND, []),
    })

    root = TCompound({
        "MinecraftDataVersion": TInt(DATA_VERSION),
        "Version": TInt(5),
        "Metadata": TCompound({
            "Name": TString(name),
            "Author": TString(author),
            "Description": TString(description),
            "TimeCreated": TLong(0),
            "TimeModified": TLong(0),
            "EnclosingSize": TCompound({"x": TInt(SX), "y": TInt(SY), "z": TInt(SZ)}),
            "RegionCount": TInt(1),
            "TotalVolume": TInt(SX * SY * SZ),
            "TotalBlocks": TInt(non_air),
        }),
        "Regions": TCompound({"Main": region}),
    })
    write_nbt_file(path, "", root)


# --------------------------------------------------------------------------
# Plan ASCII + liste de materiel
# --------------------------------------------------------------------------

LEGEND = [
    ("minecraft:air", " ", "air"),
    ("minecraft:stone", "S", "Pierre"),
    ("minecraft:moss_block", "M", "Bloc de mousse"),
    ("minecraft:rail", "=", "Rail"),
    ("minecraft:powered_rail", ">", "Rail propulseur"),
    ("minecraft:redstone_block", "R", "Bloc de redstone"),
    ("minecraft:hopper", "h", "Entonnoir"),
    ("minecraft:chest", "C", "Coffre"),
    ("minecraft:dispenser", "D", "Distributeur"),
    ("minecraft:pink_petals", "p", "Petales roses"),
    ("minecraft:redstone_wire", ".", "Poudre de redstone"),
    ("minecraft:repeater", "r", "Repeteur"),
    ("minecraft:comparator", "c", "Comparateur"),
]
CHAR = {name: ch for name, ch, _ in LEGEND}
LABEL = {name: label for name, _, label in LEGEND}


def ascii_plan():
    out = []
    for y in range(SY):
        out.append("### Couche Y=%d" % y)
        out.append("")
        out.append("```")
        out.append("      x: 012345")
        for z in range(SZ):
            row = "".join(CHAR[world[y][z][x][0]] for x in range(SX))
            out.append("z=%2d     %s" % (z, row))
        out.append("```")
        out.append("")
    return "\n".join(out)


def materials():
    c = Counter()
    for y in range(SY):
        for z in range(SZ):
            for x in range(SX):
                name = world[y][z][x][0]
                if name != "minecraft:air":
                    c[name] += 1
    return c


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    base = os.path.join(here, "pink_petal_farm")

    export_schem(base + ".schem")
    export_structure(base + ".nbt")
    export_litematic(
        base + ".litematic",
        "Ferme a petales roses",
        "generate_farm.py",
        "Ferme a petales roses automatique - module 6x7x18 (DonutSMP)",
    )

    with open(os.path.join(here, "PLAN.md"), "w", encoding="utf-8") as f:
        f.write("# Plan couche par couche\n\n")
        f.write("Le nord est en haut (z croissant = sud, x croissant = est).\n\n")
        f.write("## Legende\n\n")
        for name, ch, label in LEGEND:
            if name == "minecraft:air":
                continue
            f.write("- `%s` : %s\n" % (ch, label))
        f.write("\n")
        f.write(ascii_plan())

    print("Fichiers generes dans", here)
    print("Palette : %d etats de bloc" % len(palette_order))
    print("\nMateriel necessaire :")
    for name, n in materials().most_common():
        print("  %-28s %4d" % (LABEL.get(name, name), n))


if __name__ == "__main__":
    main()
