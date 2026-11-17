"""
Builds the offline Bangladeshi medicine catalogue shipped inside the app
(app/src/main/assets/database/catalogue.db).

Sources (both in medsdatabase/):
  * medicine.csv             — brand / generic / strength / form / manufacturer / price
                               (unit price and pack price in ৳)
  * BDMedData/*.json         — DGDA registered products: allopathic (with retail price),
                               ayurvedic, unani, herbal, homeopathic & biochemic

The table layout MUST match the Room entity `CatalogueMedicine`
(app/src/main/java/com/example/mediminder/data/catalogue/Catalogue.kt).
Room checks the schema when it opens the file, so if you change one, change both and
bump CatalogueDatabase.VERSION and CATALOGUE_VERSION below.

Usage:  python tools/build_catalogue.py
"""

import csv
import json
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "medsdatabase")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "database", "catalogue.db")

# Keep in sync with CatalogueDatabase.VERSION
CATALOGUE_VERSION = 1

# Exactly what Room generates for the CatalogueMedicine entity
CREATE_TABLE = (
    "CREATE TABLE IF NOT EXISTS `medicines` ("
    "`id` INTEGER NOT NULL, "
    "`brand_name` TEXT NOT NULL, "
    "`generic_name` TEXT, "
    "`strength` TEXT, "
    "`dosage_form` TEXT, "
    "`manufacturer` TEXT, "
    "`category` TEXT NOT NULL, "
    "`unit_price` REAL, "
    "`pack_info` TEXT, "
    "`brand_search` TEXT NOT NULL, "
    "`generic_search` TEXT NOT NULL, "
    "PRIMARY KEY(`id`))"
)
CREATE_INDEXES = [
    "CREATE INDEX IF NOT EXISTS `index_medicines_brand_search` ON `medicines` (`brand_search`)",
    "CREATE INDEX IF NOT EXISTS `index_medicines_generic_search` ON `medicines` (`generic_search`)",
]

TRADITIONAL = {
    "ayurvedic.json": "ayurvedic",
    "unani.json": "unani",
    "herbal.json": "herbal",
    "homeopathic_and_biochemic.json": "homeopathic",
}

# Rows that are not something a person takes
SKIP_FORMS = {"raw materials", "raw material"}


def search_key(text):
    """Lower-case letters and digits only. Must match CatalogueText.searchKey() in the app."""
    return re.sub(r"[^a-z0-9]", "", (text or "").lower())


def clean(text):
    text = re.sub(r"\s+", " ", (text or "").strip())
    return text or None


def strength_key(text):
    return re.sub(r"[^a-z0-9.%/+]", "", (text or "").lower())


def brand_key(brand, strength):
    """
    Brand identity for de-duplication. The DGDA register often appends the strength to the
    brand ("Seclo 20", "Xcel 500") where other sources write "Seclo" + "20 mg", so a trailing
    number equal to the strength's first number is ignored.
    """
    key = search_key(brand)
    m = re.match(r"\s*([\d.]+)", strength or "")
    if m:
        number = m.group(1).rstrip(".").replace(".", "")
        if number and key.endswith(number) and len(key) > len(number):
            key = key[: -len(number)]
    return key


def tidy_generic(generic, form):
    """'Paracetamol (IV Infusion)' with form 'IV Infusion' -> 'Paracetamol'."""
    if not generic:
        return generic
    m = re.match(r"^(.*?)\s*\(([^()]*)\)\s*$", generic)
    if m and form and search_key(m.group(2)) and search_key(m.group(2)) in search_key(form):
        return m.group(1).strip() or generic
    return generic


def form_group(form):
    """Coarse dosage-form group, so 'Tablet' and 'Tablet (Film Coated)' dedupe together."""
    f = (form or "").lower()
    for word in ("tablet", "capsule", "syrup", "suspension", "injection", "infusion", "drop",
                 "cream", "ointment", "gel", "inhal", "suppositor", "powder", "solution", "lotion",
                 "spray", "liquid", "oil", "sachet"):
        if word in f:
            return word
    return f


PRICE = r"৳\s*([\d,]+(?:\.\d+)?)"


def parse_medex_price(container):
    """
    'Unit Price: ৳ 1.00,(90's pack: ৳ 90.00),'  -> (1.0, "90's pack: ৳ 90.00")
    '100 ml bottle: ৳ 40.12'                    -> (None, '100 ml bottle: ৳ 40.12')
    """
    text = clean(container)
    if not text:
        return None, None
    unit = None
    m = re.search(r"Unit Price:\s*" + PRICE, text)
    if m:
        unit = float(m.group(1).replace(",", ""))
        text = text[m.end():]
    packs = re.findall(r"\(([^()]*৳[^()]*)\)", text)
    if packs:
        pack = "; ".join(p.strip() for p in packs)
    else:
        pack = text.strip(" ,") or None
    if pack and "৳" not in pack:
        pack = None
    return unit, pack


def parse_dgda_price(value):
    """'6.00 Tk' -> 6.0 ; 'N/A' -> None"""
    m = re.search(r"([\d,]+(?:\.\d+)?)", value or "")
    if not m:
        return None
    price = float(m.group(1).replace(",", ""))
    return price if price > 0 else None


def main():
    records = {}  # dedupe key -> record
    order = []

    def add(rec):
        if not rec["brand_name"] or (rec["dosage_form"] or "").lower() in SKIP_FORMS:
            return
        rec["generic_name"] = tidy_generic(rec["generic_name"], rec["dosage_form"])
        key = (brand_key(rec["brand_name"], rec["strength"]), strength_key(rec["strength"]), form_group(rec["dosage_form"]))
        if not key[0]:
            return
        existing = records.get(key)
        if existing is None:
            records[key] = rec
            order.append(key)
            return
        # Same product from two sources: fill in whatever the first one was missing
        for field, value in rec.items():
            if existing.get(field) in (None, "") and value not in (None, ""):
                existing[field] = value

    # 1. medex-style CSV first: friendlier manufacturer names and pack prices
    with open(os.path.join(SRC, "medicine.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            unit, pack = parse_medex_price(row.get("package container"))
            add({
                "brand_name": clean(row.get("brand name")),
                "generic_name": clean(row.get("generic")),
                "strength": clean(row.get("strength")),
                "dosage_form": clean(row.get("dosage form")),
                "manufacturer": clean(row.get("manufacturer")),
                "category": "herbal" if (row.get("type") or "").strip().lower() == "herbal" else "allopathic",
                "unit_price": unit,
                "pack_info": pack,
            })

    # 2. DGDA allopathic register (human use only)
    with open(os.path.join(SRC, "BDMedData", "allopathic.json"), encoding="utf-8") as f:
        for row in json.load(f):
            if (row.get("Use for") or "").strip().lower() == "veterinary":
                continue
            add({
                "brand_name": clean(row.get("Brand Name")),
                "generic_name": clean(row.get("Generic Name")),
                "strength": clean(row.get("Strength")),
                "dosage_form": clean(row.get("Dosage Description")),
                "manufacturer": clean(row.get("Name of the Pharmaceutical")),
                "category": "allopathic",
                "unit_price": parse_dgda_price(row.get("Retail Price(TK.)")),
                "pack_info": None,
            })

    # 3. Traditional medicine registers
    for filename, category in TRADITIONAL.items():
        with open(os.path.join(SRC, "BDMedData", filename), encoding="utf-8") as f:
            for row in json.load(f):
                add({
                    "brand_name": clean(row.get("Brand Name")),
                    "generic_name": clean(row.get("Generic Name and Strength")),
                    "strength": None,
                    "dosage_form": clean(row.get("Dosage Description")),
                    "manufacturer": clean(row.get("Name of the Pharmaceutical")),
                    "category": category,
                    "unit_price": None,
                    "pack_info": None,
                })

    rows = [records[k] for k in order]
    rows.sort(key=lambda r: (search_key(r["brand_name"]), r["strength"] or "", r["dosage_form"] or ""))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    if os.path.exists(OUT):
        os.remove(OUT)
    conn = sqlite3.connect(OUT)
    cur = conn.cursor()
    cur.execute(CREATE_TABLE)
    for statement in CREATE_INDEXES:
        cur.execute(statement)
    cur.executemany(
        "INSERT INTO medicines (id, brand_name, generic_name, strength, dosage_form, manufacturer, category,"
        " unit_price, pack_info, brand_search, generic_search) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        [
            (
                i + 1, r["brand_name"], r["generic_name"], r["strength"], r["dosage_form"], r["manufacturer"],
                r["category"], r["unit_price"], r["pack_info"],
                search_key(r["brand_name"]), search_key(r["generic_name"]),
            )
            for i, r in enumerate(rows)
        ],
    )
    cur.execute(f"PRAGMA user_version = {CATALOGUE_VERSION}")
    conn.commit()
    cur.execute("VACUUM")
    conn.close()

    by_category = {}
    for r in rows:
        by_category[r["category"]] = by_category.get(r["category"], 0) + 1
    priced = sum(1 for r in rows if r["unit_price"] or r["pack_info"])
    print(f"Wrote {len(rows)} medicines to {os.path.relpath(OUT, ROOT)} ({os.path.getsize(OUT) / 1e6:.1f} MB)")
    print("  by category:", ", ".join(f"{k} {v}" for k, v in sorted(by_category.items())))
    print(f"  with a price: {priced}")


if __name__ == "__main__":
    sys.exit(main())
