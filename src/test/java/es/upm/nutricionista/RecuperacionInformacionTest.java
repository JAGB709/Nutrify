package es.upm.nutricionista;

import es.upm.nutricionista.ir.InvertedIndex;
import es.upm.nutricionista.ir.SimilarityEngine;
import es.upm.nutricionista.ir.TfIdfCalculator;
import es.upm.nutricionista.modelo.QueryResult;
import es.upm.nutricionista.modelo.Recipe;
import es.upm.nutricionista.nlp.SpanishStopWords;
import es.upm.nutricionista.nlp.TextNormalizer;
import es.upm.nutricionista.utils.RecipeLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del motor de Recuperación de Información:
 * NLP, TF-IDF, índice invertido y ranking por similitud coseno + Jaccard.
 */
class RecuperacionInformacionTest {

    private static List<Recipe>    recetas;
    private static InvertedIndex   indice;
    private static SimilarityEngine motor;

    @BeforeAll
    static void cargarCorpus() throws Exception {
        recetas = RecipeLoader.load();
        assertFalse(recetas.isEmpty(), "El corpus no debe estar vacío");
        indice = new InvertedIndex(recetas);
        motor  = new SimilarityEngine(indice, recetas);
    }

    // ── NLP ──────────────────────────────────────────────────────────────────

    @Test
    void testNormalizacionMayusculasYAcentos() {
        List<String> terms = TextNormalizer.normalize("Salmón, Espárragos, Limón");
        assertFalse(terms.isEmpty(), "No debe devolver lista vacía");
        assertTrue(terms.contains("salmon"), "Debe normalizar 'Salmón' a 'salmon'");
    }

    @Test
    void testStemming() {
        assertEquals("espárrago", TextNormalizer.stem("espárragos"), "espárragos → espárrago");
        assertEquals("zanahoria",  TextNormalizer.stem("zanahorias"),  "zanahorias → zanahoria");
        assertEquals("cocin",      TextNormalizer.stem("cocinando"),   "cocinando → cocin");
        assertEquals("sal",        TextNormalizer.stem("sal"),         "sal (corta) sin cambios");
    }

    @Test
    void testFiltradoStopWords() {
        assertTrue(SpanishStopWords.WORDS.contains("de"),  "'de' es stop word");
        assertTrue(SpanishStopWords.WORDS.contains("con"), "'con' es stop word");
        assertFalse(SpanishStopWords.WORDS.contains("salmon"), "'salmon' no es stop word");

        List<String> terms = TextNormalizer.normalize("pollo, con, arroz, y, sal");
        assertFalse(terms.contains("con"), "normalize: 'con' debe eliminarse");
        assertFalse(terms.contains("y"),   "normalize: 'y' debe eliminarse");
        assertTrue(terms.contains("pollo"), "normalize: 'pollo' debe mantenerse");
    }

    // ── TF-IDF ───────────────────────────────────────────────────────────────

    @Test
    void testTfPesoLogairtmico() {
        assertEquals(0.0,  TfIdfCalculator.tfWeight(0), 1e-9, "tf=0 → peso 0");
        assertEquals(1.0,  TfIdfCalculator.tfWeight(1), 1e-9, "tf=1 → 1.0");
        assertEquals(1.0 + Math.log10(3), TfIdfCalculator.tfWeight(3), 1e-9, "tf=3 → 1+log10(3)");
    }

    @Test
    void testIdfFormula() {
        assertEquals(Math.log10(1000.0 / 10.0), TfIdfCalculator.idf(1000, 10), 1e-9,
                "idf(1000,10) = log10(100)");
        assertEquals(0.0, TfIdfCalculator.idf(1000, 1000), 1e-9, "df=N → idf=0");
        assertEquals(0.0, TfIdfCalculator.idf(1000, 0), 1e-9, "df=0 → idf=0");
    }

    @Test
    void testTfidfCombinado() {
        double expected = (1.0 + Math.log10(2.0)) * Math.log10(1000.0 / 10.0);
        assertEquals(expected, TfIdfCalculator.tfidf(2, 1000, 10), 1e-9,
                "tfidf(tf=2, N=1000, df=10)");
    }

    // ── Índice invertido ─────────────────────────────────────────────────────

    @Test
    void testIndiceStats() {
        assertTrue(recetas.size() >= 1000, "Corpus >= 1000 recetas");
        assertEquals(recetas.size(), indice.getDocumentCount(), "documentCount == recetas.size()");
        assertTrue(indice.getAllTerms().size() >= 500, "Vocabulario >= 500 términos");
    }

    @Test
    void testPostingListTerminoComun() {
        int dfArroz = indice.getDocumentFrequency("arroz");
        assertTrue(dfArroz > 10, "'arroz' debe aparecer en > 10 recetas, tiene df=" + dfArroz);
        assertEquals(0, indice.getDocumentFrequency("termino_inexistente_xyz"),
                "Término inexistente → df=0");
        assertTrue(indice.getPostingList("termino_inexistente_xyz").isEmpty(),
                "Posting de término inexistente debe estar vacío");
    }

    @Test
    void testNormaDocumentoPositiva() {
        int id = recetas.get(0).getId();
        assertTrue(indice.getDocNorm(id) > 0.0, "Norma de primera receta debe ser > 0");
    }

    // ── SimilarityEngine ─────────────────────────────────────────────────────

    @Test
    void testRankingOrdenDescendente() {
        List<QueryResult> results = motor.rank(Arrays.asList("pollo", "tomate", "cebolla"), 10);
        assertFalse(results.isEmpty(), "Debe haber resultados para consulta válida");
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getCombinedScore() >= results.get(i).getCombinedScore(),
                    "Resultados deben estar ordenados descendentemente por score");
        }
    }

    @Test
    void testRankingConsultaVaciaDevuelveVacio() {
        assertTrue(motor.rank(Collections.emptyList(), 5).isEmpty(), "Consulta vacía → lista vacía");
        assertTrue(motor.rank(Arrays.asList("pollo"), 0).isEmpty(), "top-k=0 → lista vacía");
    }

    @Test
    void testRankingDevuelveResultados() {
        List<QueryResult> r = motor.rank(Arrays.asList("arroz"), 3);
        assertFalse(r.isEmpty(), "Consulta 'arroz' debe devolver resultados");
        assertTrue(r.size() <= 3, "No debe exceder top-k=3");
        assertTrue(r.get(0).getCombinedScore() > 0.0, "Mejor score debe ser > 0");
    }

    @Test
    void testScoresEnRangoValido() {
        List<QueryResult> results = motor.rank(Arrays.asList("salmon", "limon"), 10);
        for (QueryResult qr : results) {
            assertTrue(qr.getCosineScore() >= 0.0, "Score coseno debe ser >= 0");
            assertTrue(qr.getCosineScore() <= 1.0 + 1e-9, "Score coseno debe ser <= 1");
            assertTrue(qr.getJaccardScore() >= 0.0, "Score Jaccard debe ser >= 0");
            assertTrue(qr.getJaccardScore() <= 1.0 + 1e-9, "Score Jaccard debe ser <= 1");
        }
    }

    @Test
    void testScoresNoPerfectos() {
        // Con la norma completa del documento los scores no deben ser todos 1.0
        List<QueryResult> results = motor.rank(Arrays.asList("salmon", "limon", "ajo"), 10);
        assertFalse(results.isEmpty(), "Debe haber resultados");
        long allOnes = results.stream()
                .filter(r -> Math.abs(r.getCosineScore() - 1.0) < 1e-6)
                .count();
        assertTrue(allOnes < results.size(),
                "No deben ser TODOS 1.0 — indicaría bug en la norma del documento");
    }
}
