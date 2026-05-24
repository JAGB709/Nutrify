package es.upm.nutricionista.modelo;

/**
 * Representa el resultado de una consulta de búsqueda, incluyendo la receta encontrada y sus puntuaciones de similitud.
 * Se utiliza para mostrar los resultados al usuario, ordenados por la puntuación combinada de similitud coseno y Jaccard.
 */

public class QueryResult implements Comparable<QueryResult> {
    private final Recipe recipe;
    private final double cosineScore;
    private final double jaccardScore;
    private final double combinedScore;

    public QueryResult(Recipe recipe, double cosineScore, double jaccardScore) {
        this.recipe = recipe;
        this.cosineScore = cosineScore;
        this.jaccardScore = jaccardScore;
        this.combinedScore = 0.7 * cosineScore + 0.3 * jaccardScore;
    }

    public Recipe getRecipe()         { return recipe; }
    public double getCosineScore()    { return cosineScore; }
    public double getJaccardScore()   { return jaccardScore; }
    public double getCombinedScore()  { return combinedScore; }

    @Override
    public int compareTo(QueryResult other) {
        return Double.compare(other.combinedScore, this.combinedScore);
    }
}
