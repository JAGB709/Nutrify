#!/usr/bin/env python3
"""
Download RecetasDeLaAbuela from HuggingFace and convert to Nutrify's recetas.json schema.

Usage:
    python3 scripts/download_dataset.py [--limit N] [--output data/recetas.json]

Output schema per recipe:
  {
    "id": 1,
    "nombre": "Tacos Dorados de Papa",
    "ingredientes": ["cilantro", "tomate", "tortillas maiz", ...],
    "pasos": ["Step one sentence.", "Step two sentence.", ...],
    "macronutrientes": {"calorias": 550, "proteinas": 15, "grasas": 28, "carbohidratos": 40}
  }

Ingredients are stored as normalized (lowercase, no accents) 1-3 word phrases.
This matches what TextNormalizer produces from user queries, ensuring direct TF-IDF matching.
"""

import argparse
import ast
import json
import re
import sys
import unicodedata
from pathlib import Path


# ---------------------------------------------------------------------------
# Vocabulary for ingredient cleaning
# ---------------------------------------------------------------------------

MEASUREMENT_UNITS = {
    'taza', 'tazas', 'cucharada', 'cucharadas', 'cucharadita', 'cucharaditas',
    'gramo', 'gramos', 'gr', 'kg', 'kilogramo', 'kilogramos',
    'ml', 'mililitro', 'mililitros', 'litro', 'litros',
    'sobre', 'sobres', 'lata', 'latas', 'bote', 'botes', 'paquete', 'paquetes',
    'pizca', 'pizcas', 'chorrito', 'chorritos', 'gota', 'gotas',
    'diente', 'dientes', 'cabeza', 'cabezas', 'hoja', 'hojas',
    'rama', 'ramas', 'ramita', 'ramitas', 'trozo', 'trozos',
    'pedazo', 'pedazos', 'porcion', 'porciones', 'manojo', 'manojos',
    'racimo', 'racimos', 'punado', 'punados', 'unidad', 'unidades',
    'pieza', 'piezas', 'docena', 'docenas', 'vaso', 'vasos',
    'copa', 'copas', 'onza', 'onzas', 'libra', 'libras',
    'kilo', 'kilos', 'medio', 'media', 'cuarto', 'octavo',
    'cc', 'dl', 'oz', 'lb',
    'sopera', 'soperas',  # cucharada sopera = tablespoon qualifier
}

PREP_WORDS = {
    'picado', 'picada', 'picados', 'picadas', 'picadito', 'picadita',
    'finamente', 'grueso', 'gruesa', 'gruesos', 'gruesas',
    'troceado', 'troceada', 'rallado', 'rallada', 'molido', 'molida',
    'frito', 'frita', 'fritos', 'fritas',
    'hervido', 'hervida', 'hervidos', 'hervidas',
    'asado', 'asada', 'asados', 'asadas',
    'pelado', 'pelada', 'pelados', 'peladas',
    'cortado', 'cortada', 'cortados', 'cortadas',
    'limpio', 'limpia', 'limpios', 'limpias',
    'cocido', 'cocida', 'cocidos', 'cocidas',
    'partido', 'partida', 'partidos', 'partidas',
    'maduro', 'madura', 'maduros', 'maduras',
    'fresco', 'fresca', 'frescos', 'frescas',
    'seco', 'seca', 'secos', 'secas',
    'congelado', 'congelada', 'congelados', 'congeladas',
    'enlatado', 'enlatada', 'enlatados', 'enlatadas',
    'tamano', 'tamanio',
    'grande', 'grandes', 'mediano', 'medianos', 'mediana', 'medianas',
    'pequeno', 'pequena', 'pequenos', 'pequenas',
    'aproximadamente', 'aprox', 'opcional', 'opcionales',
    'gusto', 'necesario', 'necesaria',
    'cubitos', 'cubito', 'tiras', 'tira', 'rodajas', 'rodaja',
    'filetes', 'filete', 'rebanadas', 'rebanada',
}

STOP_WORDS = {
    'de', 'la', 'el', 'con', 'y', 'en', 'a', 'un', 'una', 'los', 'las',
    'del', 'al', 'por', 'para', 'es', 'lo', 'que', 'se', 'su', 'me', 'mi',
    'tu', 'le', 'nos', 'les', 'o', 'e', 'ni', 'u', 'sin', 'sobre', 'entre',
    'hasta', 'desde', 'hacia', 'durante', 'mediante', 'segun', 'mas', 'pero',
    'sino', 'aunque', 'si', 'como', 'cuando', 'donde', 'unos', 'unas',
    'este', 'esta', 'ese', 'esa', 'aquel', 'aquella', 'muy', 'bien',
    'aqui', 'alli', 'hoy', 'ya', 'al', 'o', 'u',
}

UNICODE_FRACTIONS = set('½⅓¼⅔¾⅛⅜⅝⅞⅙⅚⅕⅖⅗⅘')

# ---------------------------------------------------------------------------
# Nutrition label mapping (qualitative → numeric estimates)
# ---------------------------------------------------------------------------

NUTRITION_DEFAULTS = {
    'calorias': 350,
    'proteinas': 15,
    'grasas': 12,
    'carbohidratos': 40,
}

# Each label overrides the corresponding field if it appears in Valor nutricional
NUTRITION_MAP = {
    'alto en calorias':         {'calorias': 550},
    'bajo en calorias':         {'calorias': 150},
    'muy bajo en calorias':     {'calorias': 80},
    'alto en grasas':           {'grasas': 28},
    'bajo en grasas':           {'grasas': 4},
    'muy bajo en grasas':       {'grasas': 2},
    'alto en proteinas':        {'proteinas': 35},
    'bajo en proteinas':        {'proteinas': 8},
    'alto en carbohidratos':    {'carbohidratos': 65},
    'bajo en carbohidratos':    {'carbohidratos': 10},
    'muy bajo en carbohidratos':{'carbohidratos': 5},
    'alto en fibra':            {'carbohidratos': 55},
    'sin azucar':               {'carbohidratos': 8},
    # Ignored: 'alto en sodio', 'bajo en sodio', 'alto en colesterol', etc.
}


# ---------------------------------------------------------------------------
# Text helpers
# ---------------------------------------------------------------------------

def strip_accents(text: str) -> str:
    """Lowercase and strip diacritics (NFD decomposition)."""
    text = text.lower()
    text = unicodedata.normalize('NFD', text)
    return ''.join(c for c in text if unicodedata.category(c) != 'Mn')


def clean_ingredient(raw: str) -> str:
    """
    Extract the core ingredient name from a raw ingredient string.

    Input examples:
      "½ taza de cilantro finamente picado"  → "cilantro"
      "2 chiles serranos en cubitos"          → "chiles serranos"
      "Sal al gusto"                          → "sal"
      "1 cucharada de jugo de limon"          → "jugo limon"
      "3 papas rojas de tamaño mediano"       → "papas rojas"

    Returns a normalized (lowercase, no accents) string of 1-3 words,
    or an empty string if nothing meaningful remains.
    """
    # Replace Unicode fraction chars with a space
    cleaned = ''.join(' ' if c in UNICODE_FRACTIONS else c for c in raw)
    # Remove single/curly quotes, brand markers, tilde, and stray punctuation
    cleaned = re.sub(r"[~*()\[\].\u2018\u2019\u201c\u201d'\"\u00ae\u2122/]", ' ', cleaned)
    # Remove standalone numbers and fractions like 1/2, 3/4
    cleaned = re.sub(r'\b\d+([./]\d+)?\b', ' ', cleaned)
    # Normalize: lowercase + strip accents
    cleaned = strip_accents(cleaned)
    # Tokenize
    words = cleaned.split()
    result = []
    skip_de = False  # skip "de" that follows a measurement unit
    for word in words:
        if word in MEASUREMENT_UNITS:
            skip_de = True
            continue
        if skip_de and word == 'de':
            skip_de = False
            continue
        skip_de = False
        if word in STOP_WORDS:
            continue
        if word in PREP_WORDS:
            # Stop collecting — prep words mark the end of the ingredient name
            break
        if len(word) <= 1:
            continue
        result.append(word)
        if len(result) == 3:
            break
    return ' '.join(result)


def parse_ingredientes(raw: str) -> list:
    """
    Split and clean a comma-separated ingredient string.
    Returns a list of normalized ingredient name strings (no duplicates, min length 2).
    """
    if not raw:
        return []
    seen = set()
    result = []
    for item in raw.split(','):
        cleaned = clean_ingredient(item.strip())
        if cleaned and len(cleaned) >= 2 and cleaned not in seen:
            seen.add(cleaned)
            result.append(cleaned)
    return result


def parse_pasos(raw: str) -> list:
    """
    Parse recipe steps from either a Python list string or a narrative paragraph.

    The dataset stores ~93% of Pasos as Python list strings like:
      "['1 Step one.', '2 Step two.', ...]"
    The remaining ~7% are plain paragraphs split by sentence boundaries.
    """
    if not raw:
        return []
    raw = raw.strip()
    # Handle Python list representation stored as a string
    if raw.startswith('['):
        try:
            items = ast.literal_eval(raw)
            if isinstance(items, list):
                steps = [str(s).strip() for s in items if str(s).strip()]
                return steps if steps else [raw]
        except (ValueError, SyntaxError):
            pass
    # Fall back: split narrative paragraph on sentence boundaries
    sentences = re.split(r'(?<=[.!?])\s+', raw)
    steps = [s.strip() for s in sentences if len(s.strip()) > 10]
    return steps if steps else [raw]


def parse_macros(valor_nutricional: str) -> dict:
    """Map qualitative Valor nutricional labels to numeric estimates."""
    macros = dict(NUTRITION_DEFAULTS)
    if not valor_nutricional:
        return macros
    normalized = strip_accents(valor_nutricional)
    for label, values in NUTRITION_MAP.items():
        if label in normalized:
            macros.update(values)
    return macros


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description='Download RecetasDeLaAbuela and convert to Nutrify recetas.json format'
    )
    parser.add_argument('--limit', type=int, default=None,
                        help='Maximum number of recipes to include (default: all)')
    parser.add_argument('--output', default='data/recetas.json',
                        help='Output JSON file path (default: data/recetas.json)')
    parser.add_argument('--min-ingredients', type=int, default=3,
                        help='Minimum number of cleaned ingredients required (default: 3)')
    args = parser.parse_args()

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    print('Loading RecetasDeLaAbuela (version_1) from HuggingFace...')
    print('This may take a minute on first run (caches locally after that).')
    try:
        from datasets import load_dataset
    except ImportError:
        print('ERROR: datasets library not installed. Run: pip3 install datasets')
        sys.exit(1)

    dataset = load_dataset('somosnlp/RecetasDeLaAbuela', 'version_1', split='train')
    total_raw = len(dataset)
    print(f'Raw records in dataset: {total_raw}')

    recipes = []
    skipped_no_name = 0
    skipped_few_ing = 0
    skipped_no_pasos = 0

    for row in dataset:
        if args.limit is not None and len(recipes) >= args.limit:
            break

        nombre = (row.get('Nombre') or '').strip()
        raw_ing = (row.get('Ingredientes') or '').strip()
        raw_pasos = (row.get('Pasos') or '').strip()
        valor_nut = (row.get('Valor nutricional') or '').strip()

        if not nombre:
            skipped_no_name += 1
            continue

        ingredientes = parse_ingredientes(raw_ing)
        if len(ingredientes) < args.min_ingredients:
            skipped_few_ing += 1
            continue

        pasos = parse_pasos(raw_pasos)
        if not pasos:
            skipped_no_pasos += 1
            continue

        macros = parse_macros(valor_nut)

        recipes.append({
            'id': len(recipes) + 1,
            'nombre': nombre,
            'ingredientes': ingredientes,
            'pasos': pasos,
            'macronutrientes': {
                'calorias': macros['calorias'],
                'proteinas': macros['proteinas'],
                'grasas': macros['grasas'],
                'carbohidratos': macros['carbohidratos'],
            }
        })

        if len(recipes) % 2000 == 0:
            print(f'  Processed {len(recipes)} recipes...')

    print(f'\nResults:')
    print(f'  Total processed:       {len(recipes)}')
    print(f'  Skipped (no name):     {skipped_no_name}')
    print(f'  Skipped (few ingred.): {skipped_few_ing}')
    print(f'  Skipped (no pasos):    {skipped_no_pasos}')

    if not recipes:
        print('ERROR: No recipes were extracted. Check the dataset or filters.')
        sys.exit(1)

    avg_ing = sum(len(r['ingredientes']) for r in recipes) / len(recipes)
    avg_steps = sum(len(r['pasos']) for r in recipes) / len(recipes)
    print(f'  Avg ingredients/recipe: {avg_ing:.1f}')
    print(f'  Avg steps/recipe:       {avg_steps:.1f}')

    print(f'\nWriting to {output_path}...')
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(recipes, f, ensure_ascii=False, indent=2)

    size_kb = output_path.stat().st_size // 1024
    print(f'Done. {output_path} ({size_kb} KB, {len(recipes)} recipes)')


if __name__ == '__main__':
    main()
