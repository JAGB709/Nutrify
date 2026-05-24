package es.upm.nutricionista;

import es.upm.nutricionista.ir.InvertedIndex;
import es.upm.nutricionista.ir.SimilarityEngine;
import es.upm.nutricionista.modelo.QueryResult;
import es.upm.nutricionista.modelo.Recipe;
import es.upm.nutricionista.utils.RecipeLoader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * MotorRecuperacion: fachada del motor de Recuperacion de Informacion.
 *
 * Carga el corpus de recetas, construye el indice invertido y ofrece
 * el metodo buscar() que ejecuta TF-IDF coseno + Jaccard sobre los
 * terminos normalizados de la consulta.
 */
public class MotorRecuperacion {

    private static final int TOP_K = 10;

    private List<Recipe>   recetas;
    private InvertedIndex  indice;
    private SimilarityEngine motor;
    private boolean inicializado = false;

    /**
     * Carga el corpus y construye el indice invertido.
     * Debe llamarse antes de buscar().
     *
     * @throws IOException si no se puede leer recetas.json
     */
    public void inicializar() throws IOException {
        recetas = RecipeLoader.load();
        indice  = new InvertedIndex(recetas);
        motor   = new SimilarityEngine(indice, recetas);
        inicializado = true;
        System.out.println("[MotorRecuperacion] Corpus: " + recetas.size()
                + " recetas | Vocabulario: " + indice.getAllTerms().size() + " terminos");
    }

    /**
     * Busca las recetas mas relevantes para la lista de terminos normalizados.
     *
     * @param terminos lista de terminos ya normalizados (en minusculas, sin acentos, sin stop words)
     * @return lista de hasta TOP_K resultados ordenados por score descendente
     */
    public List<QueryResult> buscar(List<String> terminos) {
        if (!inicializado || terminos == null || terminos.isEmpty()) {
            return Collections.emptyList();
        }
        return motor.rank(terminos, TOP_K);
    }

    /** Devuelve el vocabulario completo del indice. */
    public int getTotalRecetas() {
        return recetas != null ? recetas.size() : 0;
    }

    /** Devuelve el numero de terminos distintos en el vocabulario. */
    public int getTamanioVocabulario() {
        return indice != null ? indice.getAllTerms().size() : 0;
    }

    public InvertedIndex getIndice() { return indice; }
    public List<Recipe>  getRecetas() { return recetas; }
    public boolean       isInicializado() { return inicializado; }
}
