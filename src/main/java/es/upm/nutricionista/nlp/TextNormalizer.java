package es.upm.nutricionista.nlp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class TextNormalizer {

    /**
     * Normaliza una cadena de ingredientes en bruto separada por comas o espacios.
     * Canal de procesamiento: pasar a minúsculas -> eliminar acentos -> dividir -> recortar
     *                       -> filtrar palabras vacías (stop words) -> aplicar stemming -> filtrar vacíos/very cortas.
     *
     * @param rawInput entrada en bruto del usuario p. ej. "Salmon, esparragos, Limon" o "salmon esparragos limon"
     * @return lista de términos limpios normalizados y con stemming
     */
    public static List<String> normalize(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Lowercase
        String lower = rawInput.toLowerCase();

        // eliminar acentos
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");

        // Dividir por comas o espacios, luego limpiar cada token
        String[] tokens = normalized.contains(",")
                ? normalized.split(",")
                : normalized.split("\\s+");

        List<String> terms = new ArrayList<>();
        for (String token : tokens) {
            String clean = token.trim().replaceAll("[^a-z0-9 ]", "").trim();
            if (clean.isEmpty()) continue;
            // Filtrar stop words y aplicar stemming
            for (String word : clean.split("\\s+")) {
                word = word.trim();
                if (word.length() < 2) continue;
                if (SpanishStopWords.WORDS.contains(word)) continue;
                terms.add(stem(word));
            }
        }
        return terms;
    }

    /**
     * Aplica un stemming muy básico para español, eliminando sufijos comunes. No es un algoritmo completo, pero ayuda a agrupar formas similares.
     */
    public static String stem(String word) {
        if (word == null || word.length() <= 4) return word;
        if (word.endsWith("iones") && word.length() > 6) return word.substring(0, word.length() - 3);
        if (word.endsWith("ados")  && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("idas")  && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("idos")  && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("es")    && word.length() > 4) return word.substring(0, word.length() - 2);
        if (word.endsWith("os")    && word.length() > 4) return word.substring(0, word.length() - 1);
        if (word.endsWith("as")    && word.length() > 4) return word.substring(0, word.length() - 1);
        if (word.endsWith("ando")  && word.length() > 5) return word.substring(0, word.length() - 4);
        if (word.endsWith("iendo") && word.length() > 6) return word.substring(0, word.length() - 5);
        if (word.endsWith("ado")   && word.length() > 4) return word.substring(0, word.length() - 3);
        if (word.endsWith("ido")   && word.length() > 4) return word.substring(0, word.length() - 3);
        return word;
    }
}
