import sqlite3
import csv
import os

db_path = "mediminder_dataset.db"
csv_path = "medsdatabase/medicine.csv"

# Remove existing db if it exists
if os.path.exists(db_path):
    os.remove(db_path)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Create table with FTS (Full Text Search) can be useful, but for simple prefix search, standard table is fine.
cursor.execute('''
CREATE TABLE IF NOT EXISTS medicine_dictionary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    brand_id TEXT,
    brand_name TEXT,
    type TEXT,
    slug TEXT,
    dosage_form TEXT,
    generic_name TEXT,
    strength TEXT,
    manufacturer TEXT,
    package_container TEXT
)
''')

# Room requires android_metadata table to identify it as a valid DB sometimes, but 
# Room's `createFromAsset` will actually handle missing metadata tables by copying and applying schemas.
# However, it's safer to ensure the schema matches EXACTLY what Room expects.
# We will define the Room Entity and let Room manage the table, OR we can just use exactly the names Room generates.
# Let's match Room's generated schema for an entity named `MedicineDictionary` with table name `medicine_dictionary`.

with open(csv_path, 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    # Headers: brand id,brand name,type,slug,dosage form,generic,strength,manufacturer,package container,Package Size
    
    for row in reader:
        cursor.execute('''
        INSERT INTO medicine_dictionary (
            brand_id, brand_name, type, slug, dosage_form, generic_name, strength, manufacturer, package_container
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ''', (
            row.get('brand id', ''),
            row.get('brand name', ''),
            row.get('type', ''),
            row.get('slug', ''),
            row.get('dosage form', ''),
            row.get('generic', ''),
            row.get('strength', ''),
            row.get('manufacturer', ''),
            row.get('package container', '')
        ))

conn.commit()
conn.close()
print("Successfully generated mediminder_dataset.db with " + str(cursor.rowcount) + " final rows.")
