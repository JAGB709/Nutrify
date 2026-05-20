package es.upm.nutricionista;

import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la ontología RDF de alimentos cargada con razonador RDFS de Jena.
 * Verifica la jerarquía de clases y la inferencia transitiva rdfs9 / rdfs11.
 */
class OntologiaTest {

    private static final String NUTRI = "http://nutrify.si.upm.es/ontologia#";
    private static InfModel ontologia;

    @BeforeAll
    static void cargarOntologia() throws Exception {
        InputStream is = OntologiaTest.class.getClassLoader()
                .getResourceAsStream("ontologia_alimentos.ttl");
        assertNotNull(is, "El fichero ontologia_alimentos.ttl debe existir en el classpath");
        Model base = ModelFactory.createDefaultModel();
        base.read(is, null, "TURTLE");
        ontologia = ModelFactory.createRDFSModel(base);
    }

    @Test
    void testOntologiaCargaConTripletas() {
        assertTrue(ontologia.size() >= 50,
                "La ontología debe tener al menos 50 tripletas (base + inferidas), tiene: " + ontologia.size());
    }

    @Test
    void testClasesExisten() {
        for (String clase : new String[]{"Alimento", "Proteina", "Pescado", "Carne",
                                         "Huevo", "Legumbre", "Vegetal", "Fruta", "Lacteo", "Cereal", "Grasa"}) {
            Resource r = ontologia.getResource(NUTRI + clase);
            assertNotNull(r, "La clase " + clase + " debe existir en la ontología");
        }
    }

    @Test
    void testSalmonEsPescado() {
        Resource salmon  = ontologia.getResource(NUTRI + "salmon");
        Resource pescado = ontologia.getResource(NUTRI + "Pescado");
        assertTrue(ontologia.contains(salmon, RDF.type, pescado),
                "salmon debe ser rdf:type Pescado (declarado directamente)");
    }

    @Test
    void testInferenciaRdfs9_SalmonEsProteina() {
        // rdfs9: Pescado rdfs:subClassOf Proteina + salmon rdf:type Pescado → salmon rdf:type Proteina
        Resource salmon   = ontologia.getResource(NUTRI + "salmon");
        Resource proteina = ontologia.getResource(NUTRI + "Proteina");
        assertTrue(ontologia.contains(salmon, RDF.type, proteina),
                "salmon debe ser inferido como Proteina via rdfs9 (Pescado subClassOf Proteina)");
    }

    @Test
    void testTransitividadRdfs11_SalmonEsAlimento() {
        // rdfs11: Pescado→Proteina→Alimento → salmon rdf:type Alimento (transitivo)
        Resource salmon   = ontologia.getResource(NUTRI + "salmon");
        Resource alimento = ontologia.getResource(NUTRI + "Alimento");
        assertTrue(ontologia.contains(salmon, RDF.type, alimento),
                "salmon debe ser inferido como Alimento via rdfs11 (transitivity Pescado→Proteina→Alimento)");
    }

    @Test
    void testIngredientesDiversosClasificados() {
        String[][] expected = {
            {"pollo",    "Carne"},
            {"lenteja",  "Legumbre"},
            {"garbanzo", "Legumbre"},
            {"huevo",    "Huevo"},
            {"tomate",   "Vegetal"},
            {"esparrago","Vegetal"},
            {"espinaca", "Vegetal"},
            {"patata",   "Vegetal"},
            {"leche",    "Lacteo"},
            {"arroz",    "Cereal"},
            {"aceite",   "Grasa"},
        };
        for (String[] pair : expected) {
            Resource ing  = ontologia.getResource(NUTRI + pair[0]);
            Resource cls  = ontologia.getResource(NUTRI + pair[1]);
            assertTrue(ontologia.contains(ing, RDF.type, cls),
                    pair[0] + " debe tener rdf:type " + pair[1]);
        }
    }

    @Test
    void testIngredientesInfierenProteina() {
        // Pollo (Carne), lenteja (Legumbre) y huevo (Huevo) deben inferirse como Proteina
        Resource proteina = ontologia.getResource(NUTRI + "Proteina");
        for (String term : new String[]{"pollo", "lenteja", "garbanzo", "huevo", "atun"}) {
            Resource ing = ontologia.getResource(NUTRI + term);
            assertTrue(ontologia.contains(ing, RDF.type, proteina),
                    term + " debe inferirse como Proteina");
        }
    }
}
