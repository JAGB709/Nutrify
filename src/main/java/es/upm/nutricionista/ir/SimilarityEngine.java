package es.upm.nutricionista.ir;

import es.upm.nutricionista.modelo.QueryResult;
import es.upm.nutricionista.modelo.Recipe;

import java.util.*;

/**
 * Ranks recipes by TF-IDF cosine similarity + Jaccard refinement.
 *
 * Combined score = 0.7 * cosine + 0.3 * jaccard
 */
public class SimilarityEngine {

    private final InvertedIndex index;
    private final List<Recipe> recipes;

    public SimilarityEngine(InvertedIndex index, List<Recipe> recipes) {
        this.index   = index;
        this.recipes = recipes;
    }

    public List<QueryResult> rank(List<String> queryTerms, int topK) {
        if (queryTerms == null || queryTerms.isEmpty() || topK <= 0) {
            return Collections.emptyList();
        }

        int N = index.getDocumentCount();

        // Build query TF vector and TF-IDF vector
        Map<String, Integer> queryTf = new HashMap<>();
        for (String t : queryTerms) queryTf.merge(t, 1, Integer::sum);

        Set<String> querySet = queryTf.keySet();
        Map<String, Double> queryVector = new HashMap<>();
        for (String term : querySet) {
            int tf = queryTf.get(term);
            int df = index.getDocumentFrequency(term);
            queryVector.put(term, TfIdfCalculator.tfidf(tf, N, df));
        }

        double queryNorm = l2Norm(queryVector.values());

        // Collect candidate recipes via posting lists (dot products accumulator)
        Map<Integer, Recipe> recipeById = new HashMap<>(recipes.size() * 2);
        for (Recipe r : recipes) recipeById.put(r.getId(), r);

        Map<Integer, Double> candidateDots = new HashMap<>();
        for (String term : querySet) {
            double qW = queryVector.get(term);
            if (qW == 0.0) continue;
            Map<Integer, Integer> posting = index.getPostingList(term);
            int df = posting.size();
            for (Map.Entry<Integer, Integer> e : posting.entrySet()) {
                double dW = TfIdfCalculator.tfidf(e.getValue(), N, df);
                candidateDots.merge(e.getKey(), qW * dW, Double::sum);
            }
        }

        // Compute cosine + Jaccard scores for each candidate
        List<QueryResult> results = new ArrayList<>(candidateDots.size());
        for (Map.Entry<Integer, Double> e : candidateDots.entrySet()) {
            int recipeId = e.getKey();
            Recipe recipe = recipeById.get(recipeId);
            if (recipe == null) continue;

            double docNorm = index.getDocNorm(recipeId);
            double cosine  = (queryNorm > 0.0 && docNorm > 0.0) ? e.getValue() / (queryNorm * docNorm) : 0.0;

            // Jaccard: |queryTerms ∩ docTerms| / |queryTerms ∪ docTerms|
            Set<String> docTerms = index.getRecipeTerms(recipeId);
            Set<String> intersection = new HashSet<>(querySet);
            intersection.retainAll(docTerms);
            Set<String> union = new HashSet<>(querySet);
            union.addAll(docTerms);
            double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();

            results.add(new QueryResult(recipe, cosine, jaccard));
        }

        results.sort(Comparator.naturalOrder());
        return results.subList(0, Math.min(topK, results.size()));
    }

    private static double l2Norm(Collection<Double> values) {
        double sumSq = 0.0;
        for (double v : values) sumSq += v * v;
        return Math.sqrt(sumSq);
    }
}
