package es.upm.nutricionista.nlp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class TextNormalizer {

    /**
     * Normalizes a raw comma-separated or space-separated ingredient input string.
     * Pipeline: lowercase -> strip accents -> split -> trim
     *           -> filter stop words -> stem -> filter empty/short.
     *
     * @param rawInput raw user input e.g. "Salmon, esparragos, Limon" or "salmon esparragos limon"
     * @return list of clean normalized+stemmed terms
     */
    public static List<String> normalize(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Lowercase
        String lower = rawInput.toLowerCase();

        // Strip diacritics/accents
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");

        // Determine delimiter: if contains comma use comma, else whitespace
        String[] tokens = normalized.contains(",")
                ? normalized.split(",")
                : normalized.split("\\s+");

        List<String> terms = new ArrayList<>();
        for (String token : tokens) {
            String clean = token.trim().replaceAll("[^a-z0-9 ]", "").trim();
            if (clean.isEmpty()) continue;
            // For space-separated multi-word tokens from comma split, split further
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
     * Basic Spanish stemmer by suffix stripping.
     * Handles common plural and verbal forms.
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
