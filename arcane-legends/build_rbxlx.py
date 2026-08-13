#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_rbxlx.py — Assemble la place Roblox « ArcaneLegends.rbxlx » à partir
des sources Lua de src/.

Conventions de nommage :
  *.server.lua -> Script            (ServerScriptService)
  *.client.lua -> LocalScript       (StarterPlayer > StarterPlayerScripts)
  *.lua        -> ModuleScript

Le fichier généré est du XML rbxlx standard : les sources sont échappées
(&, <, >) — pas de CDATA, donc aucun conflit avec les « ]] » du Lua.

Usage : python3 build_rbxlx.py
"""

import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "src")
OUT = os.path.join(ROOT, "ArcaneLegends.rbxlx")

_referent_counter = [0]


def next_referent():
    _referent_counter[0] += 1
    return "RBX%d" % _referent_counter[0]


def xml_escape(text):
    return (
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    )


def script_item(file_path, indent):
    """Retourne le bloc <Item> d'un script à partir de son fichier source."""
    base = os.path.basename(file_path)
    if base.endswith(".server.lua"):
        class_name = "Script"
        name = base[: -len(".server.lua")]
    elif base.endswith(".client.lua"):
        class_name = "LocalScript"
        name = base[: -len(".client.lua")]
    else:
        class_name = "ModuleScript"
        name = base[: -len(".lua")]

    with open(file_path, "r", encoding="utf-8") as handle:
        source = handle.read()

    pad = "\t" * indent
    lines = []
    lines.append('%s<Item class="%s" referent="%s">' % (pad, class_name, next_referent()))
    lines.append("%s\t<Properties>" % pad)
    lines.append('%s\t\t<string name="Name">%s</string>' % (pad, xml_escape(name)))
    lines.append('%s\t\t<ProtectedString name="Source">%s</ProtectedString>'
                 % (pad, xml_escape(source)))
    lines.append("%s\t</Properties>" % pad)
    lines.append("%s</Item>" % pad)
    return "\n".join(lines)


def service_open(class_name, indent):
    pad = "\t" * indent
    return ('%s<Item class="%s" referent="%s">\n'
            "%s\t<Properties>\n"
            '%s\t\t<string name="Name">%s</string>\n'
            "%s\t</Properties>" % (pad, class_name, next_referent(), pad, pad, class_name, pad))


def service_close(indent):
    return "%s</Item>" % ("\t" * indent)


def collect_scripts(subdir):
    folder = os.path.join(SRC, subdir)
    files = sorted(
        os.path.join(folder, f)
        for f in os.listdir(folder)
        if f.endswith(".lua")
    )
    if not files:
        raise SystemExit("Aucun fichier .lua dans %s" % folder)
    return files


def main():
    parts = []
    parts.append(
        '<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" '
        'version="4">'
    )
    parts.append("\t<External>null</External>")
    parts.append("\t<External>nil</External>")

    # Workspace vide : 100 % de la map est construite par MapBuilder au démarrage.
    parts.append(service_open("Workspace", 1))
    parts.append(service_close(1))

    # ReplicatedStorage : modules partagés
    parts.append(service_open("ReplicatedStorage", 1))
    for path in collect_scripts("ReplicatedStorage"):
        parts.append(script_item(path, 2))
    parts.append(service_close(1))

    # ServerScriptService : Main (Script) + modules serveur
    parts.append(service_open("ServerScriptService", 1))
    for path in collect_scripts("ServerScriptService"):
        parts.append(script_item(path, 2))
    parts.append(service_close(1))

    # StarterPlayer > StarterPlayerScripts : LocalScripts
    parts.append(service_open("StarterPlayer", 1))
    parts.append(service_open("StarterPlayerScripts", 2))
    for path in collect_scripts("StarterPlayerScripts"):
        parts.append(script_item(path, 3))
    parts.append(service_close(2))
    parts.append(service_close(1))

    # Services vides usuels (Studio complète le reste)
    for service in ("StarterGui", "Lighting", "SoundService", "Players"):
        parts.append(service_open(service, 1))
        parts.append(service_close(1))

    parts.append("</roblox>")
    document = "\n".join(parts) + "\n"

    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write(document)

    # Auto-vérification : XML bien formé + les sources survivent au round-trip
    tree = ET.parse(OUT)
    sources = {}
    for item in tree.iter("Item"):
        props = item.find("Properties")
        if props is None:
            continue
        name = None
        source = None
        for prop in props:
            if prop.get("name") == "Name":
                name = prop.text
            if prop.get("name") == "Source":
                source = prop.text
        if name is not None and source is not None:
            sources[name] = source

    expected = {}
    for subdir in ("ReplicatedStorage", "ServerScriptService", "StarterPlayerScripts"):
        for path in collect_scripts(subdir):
            base = os.path.basename(path)
            name = base.replace(".server.lua", "").replace(".client.lua", "").replace(".lua", "")
            with open(path, "r", encoding="utf-8") as handle:
                expected[name] = handle.read()

    errors = []
    for name, code in expected.items():
        if name not in sources:
            errors.append("Script absent du rbxlx : %s" % name)
        elif sources[name] != code:
            errors.append("Source alteree apres round-trip : %s" % name)
    if errors:
        for error in errors:
            print("ERREUR :", error, file=sys.stderr)
        raise SystemExit(1)

    size_kb = os.path.getsize(OUT) / 1024.0
    print("OK : %s (%d scripts, %.1f Ko)" % (OUT, len(expected), size_kb))


if __name__ == "__main__":
    main()
