package es.upm.nutricionista.ir;

import es.upm.nutricionista.modelo.Recipe;
import es.upm.nutricionista.nlp.TextNormalizer;

import java.util.*;

/**
 * Indice invertido para recetas basado en ingredientes. Permite recuperar rápidamente las recetas que contienen ciertos términos.
 * Calcula TF-IDF para cada término en cada receta, y almacena la norma L2 de cada receta para la similitud coseno. También mantiene un conjunto de términos por receta para
 */
public class InvertedIndex {

    // term -> Map(recipeId -> rawTf)
    private final Map<String, Map<Integer, Integer>> postingLists;
    private final Map<String, Double> idfValues;
    private final Map<Integer, Double> docNorms;
    private final Map<Integer, Set<String>> recipeTerms;
    private final int documentCount;

    public InvertedIndex(List<Recipe> recipes) {
        postingLists = new HashMap<>();
        idfValues    = new HashMap<>();
        docNorms     = new HashMap<>();
        recipeTerms  = new HashMap<>();
        documentCount = recipes != null ? recipes.size() : 0;

        if (recipes == null) return;

        // Paso 1: Construir posting lists con TF sin normalizar
        for (Recipe recipe : recipes) {
            int recipeId = recipe.getId();
            List<String> ingredientes = recipe.getIngredientes();
            if (ingredientes == null) continue;

            Map<String, Integer> localTf = new HashMap<>();
            for (String ingredient : ingredientes) {
                if (ingredient == null) continue;
                for (String token : ingredient.split("\\s+")) {
                    if (token.isEmpty()) continue;
                    String term = TextNormalizer.stem(token.toLowerCase());
                    localTf.merge(term, 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> e : localTf.entrySet()) {
                postingLists.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(recipeId, e.getValue());
            }
        }

        // Paso 2: Calcular IDF para cada término
        for (Map.Entry<String, Map<Integer, Integer>> e : postingLists.entrySet()) {
            int df = e.getValue().size();
            if (df > 0) idfValues.put(e.getKey(), Math.log10((double) documentCount / df));
        }

        // Paso 3: Calcular norma L2 de cada documento para la similitud coseno
        Map<Integer, Double> sumSq = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Integer>> termEntry : postingLists.entrySet()) {
            String term = termEntry.getKey();
            double idf = idfValues.getOrDefault(term, 0.0);
            for (Map.Entry<Integer, Integer> docEntry : termEntry.getValue().entrySet()) {
                int id = docEntry.getKey(), tf = docEntry.getValue();
                double w = (tf > 0 ? 1.0 + Math.log10(tf) : 0.0) * idf;
                sumSq.merge(id, w * w, Double::sum);
                recipeTerms.computeIfAbsent(id, k -> new HashSet<>()).add(term);
            }
        }
        for (Map.Entry<Integer, Double> e : sumSq.entrySet()) {
            docNorms.put(e.getKey(), Math.sqrt(e.getValue()));
        }
    }

    // Métodos de acceso
    public Map<Integer, Integer> getPostingList(String term) {
        Map<Integer, Integer> r = postingLists.get(term);
        return r != null ? r : Collections.emptyMap();
    }
    // Devuelve el número de documentos que contienen el término
    public int getDocumentFrequency(String term) {
        Map<Integer, Integer> p = postingLists.get(term);
        return p != null ? p.size() : 0;
    }

    public Set<String> getAllTerms()                   {
        return postingLists.keySet();
    }
    public int getDocumentCount()                      {
        return documentCount;
    }
    public Map<String, Double> getIdfValues()          {
        return idfValues;
    }
    public double getDocNorm(int recipeId)             {
        return docNorms.getOrDefault(recipeId, 0.0);
    }
    public Set<String> getRecipeTerms(int recipeId)    {
        Set<String> t = recipeTerms.get(recipeId);
        return t != null ? t : Collections.emptySet();
    }
}
