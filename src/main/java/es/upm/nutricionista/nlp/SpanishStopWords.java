package es.upm.nutricionista.nlp;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Contiene un conjunto de palabras vacías (stop words) comunes en español que se deben ignorar durante el procesamiento de texto.
 * Estas palabras no aportan significado relevante para la búsqueda y pueden ser eliminadas para mejorar la eficiencia y precisión del motor de búsqueda.
 */
public class SpanishStopWords {
    public static final Set<String> WORDS = new HashSet<>(Arrays.asList(
        "de", "la", "el", "con", "y", "en", "a", "un", "una",
        "los", "las", "del", "al", "por", "para", "es", "lo",
        "que", "se", "su", "me", "mi", "tu", "le", "nos", "les",
        "o", "e", "ni", "u", "sin", "sobre", "entre", "hasta",
        "desde", "hacia", "durante", "mediante", "segun", "mas",
        "pero", "sino", "aunque", "si", "como", "cuando", "donde",
        "unos", "unas", "este", "esta", "ese", "esa", "aquel",
        "aquella", "muy", "bien", "aqui", "alli", "hoy", "ya"
    ));
}
