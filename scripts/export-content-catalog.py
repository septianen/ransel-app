#!/usr/bin/env python3
"""
Exports what Karung may edit as remote content (admin-spec stage D) from the traveller app:
every <string> of composeApp with its bundled Indonesian and English text, and the illustrations
an error screen can show. The server only accepts keys and illustrations listed here.

Run from the repo root after changing composeApp strings or drawables:
    python3 scripts/export-content-catalog.py
"""
import json
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "composeApp/src/commonMain/composeResources"
OUT = ROOT / "server/src/main/resources/content/catalog.json"


def strings(locale_dir: str) -> dict:
    tree = ET.parse(RES / locale_dir / "strings.xml")
    out = {}
    for node in tree.getroot().findall("string"):
        text = "".join(node.itertext())
        # Android-style escapes as the app shows them
        out[node.get("name")] = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    return out


def main() -> None:
    indonesian = strings("values-in")
    english = strings("values")
    keys = sorted(set(indonesian) | set(english))
    catalog = {
        "strings": [
            {"key": k, "area": k.split("_", 1)[0], "id": indonesian.get(k, ""), "en": english.get(k, "")}
            for k in keys
        ],
        "illustrations": sorted(
            p.stem for p in (RES / "drawable").iterdir() if p.stem.startswith("ic_") and p.suffix in (".webp", ".png")
        ),
    }
    OUT.write_text(json.dumps(catalog, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(catalog['strings'])} strings, {len(catalog['illustrations'])} illustrations -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
