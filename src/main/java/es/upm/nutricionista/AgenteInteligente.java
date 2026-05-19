package es.upm.nutricionista;

import es.upm.nutricionista.modelo.QueryResult;
import es.upm.nutricionista.modelo.Recipe;
import es.upm.nutricionista.utils.DFHelper;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;

import java.io.InputStream;
import java.util.*;

/**
 * AgenteInteligente — motor IR y base de conocimiento de Nutrify.
 *
 * Responsabilidades:
 *  1. Cargar el corpus de recetas y construir el indice invertido (via MotorRecuperacion)
 *  2. Cargar la ontologia RDF de alimentos con razonador RDFS (Apache Jena)
 *  3. Registrar el servicio "cerebro-service" en el DF
 *  4. Aceptar ACLMessage.REQUEST de AgentePercepcion
 *  5. Rankear recetas por TF-IDF coseno + Jaccard
 *  6. Clasificar ingredientes de la consulta usando inferencia RDFS
 *  7. Responder con ACLMessage.INFORM (resultados serializados)
 */
public class AgenteInteligente extends Agent {

    private static final String SERVICE_TYPE   = "cerebro-service";
    private static final String ONTOLOGY_RES   = "ontologia_alimentos.ttl";
    private static final String NUTRI_NS       = "http://nutrify.si.upm.es/ontologia#";

    private MotorRecuperacion motor;
    private InfModel          ontologia;

    @Override
    protected void setup() {
        System.out.println("[AgenteInteligente] Iniciando...");
        motor = new MotorRecuperacion();
        addBehaviour(new InicializarBehaviour());
        addBehaviour(new ProcesarConsultaBehaviour());
    }

    @Override
    protected void takeDown() {
        DFHelper.deregisterService(this);
        System.out.println("[AgenteInteligente] Detenido.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 1: InicializarBehaviour (OneShotBehaviour)
    // ─────────────────────────────────────────────────────────────────────────

    private class InicializarBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            // 1a. Cargar corpus y construir indice invertido
            try {
                motor.inicializar();
                System.out.println("[AgenteInteligente] Indice listo: "
                        + motor.getTotalRecetas() + " recetas, "
                        + motor.getTamanioVocabulario() + " terminos.");
            } catch (Exception e) {
                System.err.println("[AgenteInteligente] Error al cargar corpus: " + e.getMessage());
            }

            // 1b. Cargar ontologia RDF con razonador RDFS
            // El razonador aplica reglas rdfs9/rdfs11: salmon:Pescado -> salmon:Proteina -> salmon:Alimento
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream(ONTOLOGY_RES);
                if (is == null) throw new IllegalStateException("Recurso no encontrado: " + ONTOLOGY_RES);
                Model base = ModelFactory.createDefaultModel();
                base.read(is, null, "TURTLE");
                ontologia = ModelFactory.createRDFSModel(base);
                System.out.println("[AgenteInteligente] Ontologia cargada: "
                        + ontologia.size() + " tripletas (base + inferidas RDFS).");
            } catch (Exception e) {
                System.err.println("[AgenteInteligente] Ontologia no disponible: " + e.getMessage());
                ontologia = null;
            }

            // 1c. Registrar en DF — se hace DESPUES de cargar para que este listo al recibir consultas
            DFHelper.registerService(myAgent, SERVICE_TYPE, "Nutrify-Cerebro");
            System.out.println("[AgenteInteligente] Registrado como " + SERVICE_TYPE + ". Listo para consultas.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 2: ProcesarConsultaBehaviour (CyclicBehaviour)
    // Usa receive() + block() — patron correcto JADE para CyclicBehaviour
    // ─────────────────────────────────────────────────────────────────────────

    private class ProcesarConsultaBehaviour extends CyclicBehaviour {
        private final MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if (msg == null) { block(); return; }

            String content = msg.getContent();
            if (content == null || content.trim().isEmpty()) return;

            List<String> terminos = new ArrayList<>();
            for (String t : content.split(",")) {
                String term = t.trim();
                if (!term.isEmpty()) terminos.add(term);
            }

            if (!motor.isInicializado()) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("ERROR:Motor no inicializado");
                myAgent.send(reply);
                return;
            }

            System.out.println("[AgenteInteligente] Procesando consulta: " + terminos);
            List<QueryResult> resultados = motor.buscar(terminos);
            System.out.println("[AgenteInteligente] Resultados: " + resultados.size());

            String categorias = clasificarIngredientes(terminos);
            if (!categorias.isEmpty()) {
                System.out.println("[AgenteInteligente] Categorias ontologicas: " + categorias);
            }

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(serializar(resultados, content, categorias));
            myAgent.send(reply);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clasificacion ontologica mediante inferencia RDFS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Clasifica los terminos de la consulta usando el razonador RDFS de Jena.
     * Aplica rdfs9: si salmon rdf:type Pescado y Pescado rdfs:subClassOf Proteina
     *              entonces salmon rdf:type Proteina (inferido).
     */
    private String clasificarIngredientes(List<String> terminos) {
        if (ontologia == null || terminos == null) return "";
        Set<String> categorias = new LinkedHashSet<>();
        for (String term : terminos) {
            Resource ing = ontologia.getResource(NUTRI_NS + term);
            StmtIterator iter = ontologia.listStatements(ing, RDF.type, (RDFNode) null);
            while (iter.hasNext()) {
                RDFNode obj = iter.next().getObject();
                if (obj.isResource()) {
                    String uri = obj.asResource().getURI();
                    if (uri != null && uri.startsWith(NUTRI_NS)) {
                        String nombre = uri.substring(NUTRI_NS.length());
                        if (!nombre.equals("Alimento") && !nombre.isEmpty())
                            categorias.add(nombre);
                    }
                }
            }
        }
        return String.join(",", categorias);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Serializacion de resultados
    // Formato: QUERY:<query>\n<id>|<nombre>|<score>|<cal>|<prot>|<gras>|<carbs>|<ingr>|<pasos>|cat:<cats>
    // ─────────────────────────────────────────────────────────────────────────

    private String serializar(List<QueryResult> resultados, String queryOriginal, String categorias) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUERY:").append(queryOriginal).append("\n");
        for (QueryResult qr : resultados) {
            Recipe r = qr.getRecipe();
            sb.append(r.getId()).append("|");
            sb.append(r.getNombre().replace("|", "##")).append("|");
            sb.append(String.format(Locale.US, "%.4f", qr.getCombinedScore())).append("|");
            sb.append(r.getMacronutrientes().getCalorias()).append("|");
            sb.append(r.getMacronutrientes().getProteinas()).append("|");
            sb.append(r.getMacronutrientes().getGrasas()).append("|");
            sb.append(r.getMacronutrientes().getCarbohidratos()).append("|");
            sb.append(String.join(";", r.getIngredientes())).append("|");
            sb.append(String.join(";;", r.getPasos()));
            if (!categorias.isEmpty()) sb.append("|cat:").append(categorias);
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
