package es.upm.nutricionista;

import es.upm.nutricionista.nlp.SpanishStopWords;
import es.upm.nutricionista.utils.DFHelper;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.SwingUtilities;
import java.util.*;

/**
 * AgenteInterfaz — capa de presentación de Nutrify.
 *
 * Responsabilidades:
 *  1. Registrar el servicio "interfaz-service" en el DF
 *  2. Recibir ACLMessage.INFORM de AgentePercepcion
 *  3. Parsear los resultados serializados (formato pipe)
 *  4. Mostrar resultados en consola con análisis de ingredientes faltantes (Gap Analysis)
 *  5. Si la GUI está activa, delegar la visualización a NutrifyGUI
 */
public class AgenteInterfaz extends Agent {

    private static final String SERVICE_TYPE = "interfaz-service";
    private static final String HEAVY = "═";
    private static final String LIGHT = "─";
    private static final int    WIDTH = 64;

    protected NutrifyGUI gui = null;

    @Override
    protected void setup() {
        System.out.println("[AgenteInterfaz] Iniciando...");
        addBehaviour(new RegistrarServicioBehaviour());
        addBehaviour(new MostrarResultadosBehaviour());

        // Arrancar la GUI en el hilo de eventos Swing
        SwingUtilities.invokeLater(() -> {
            gui = new NutrifyGUI();
            gui.setListener(ingredientes -> {
                // Desde Swing: notificar a AgentePercepcion via ACL
                addBehaviour(new OneShotBehaviour() {
                    @Override
                    public void action() {
                        AID percepcion = es.upm.nutricionista.utils.DFHelper.searchService(
                                myAgent, "percepcion-service");
                        if (percepcion == null) {
                            System.err.println("[AgenteInterfaz] AgentePercepcion no disponible.");
                            return;
                        }
                        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                        msg.addReceiver(percepcion);
                        msg.setContent(ingredientes);
                        msg.setConversationId("nueva-consulta");
                        myAgent.send(msg);
                    }
                });
            });
            gui.setVisible(true);
        });
    }

    @Override
    protected void takeDown() {
        DFHelper.deregisterService(this);
        if (gui != null) {
            SwingUtilities.invokeLater(() -> gui.dispose());
        }
        System.out.println("[AgenteInterfaz] Detenido.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 1: RegistrarServicioBehaviour (OneShotBehaviour)
    // ─────────────────────────────────────────────────────────────────────────

    private class RegistrarServicioBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            DFHelper.registerService(myAgent, SERVICE_TYPE, "Nutrify-Interfaz");
            System.out.println("[AgenteInterfaz] Servicio de display registrado y listo.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 2: MostrarResultadosBehaviour (CyclicBehaviour)
    // Usa receive() + block() — patrón correcto JADE
    // ─────────────────────────────────────────────────────────────────────────

    private class MostrarResultadosBehaviour extends CyclicBehaviour {
        private final MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if (msg == null) { block(); return; }

            String content = msg.getContent();
            if (content == null || content.trim().isEmpty()) return;

            // Formato: "<queryTerms>||<resultados_serializados>"
            String queryTermsRaw = "";
            if (content.contains("||")) {
                int sep = content.indexOf("||");
                queryTermsRaw = content.substring(0, sep);
                content       = content.substring(sep + 2);
            }

            mostrarResultados(queryTermsRaw, content);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Presentación de resultados
    // ─────────────────────────────────────────────────────────────────────────

    void mostrarResultados(String queryTermsRaw, String content) {
        if (content.startsWith("ERROR:")) {
            System.out.println("\n[AgenteInterfaz] " + content);
            return;
        }

        Set<String> userTerms = new HashSet<>();
        for (String t : queryTermsRaw.split(",")) {
            String term = t.trim();
            if (!term.isEmpty()) userTerms.add(term);
        }

        String[] lines = content.split("\n");
        if (lines.length == 0) return;

        String queryLine = lines[0];
        String query = queryLine.startsWith("QUERY:") ? queryLine.substring(6).trim() : queryLine.trim();

        List<String[]> results = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length >= 8) results.add(parts);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(rep(HEAVY, WIDTH)).append("\n");
        sb.append("  NUTRIFY — Resultados para: ").append(query).append("\n");
        sb.append(rep(HEAVY, WIDTH)).append("\n");

        if (results.isEmpty()) {
            sb.append("\n  No se encontraron recetas con esos ingredientes.\n\n");
            sb.append(rep(HEAVY, WIDTH)).append("\n");
            System.out.print(sb);
            return;
        }

        int rank = 0;
        for (String[] parts : results) {
            rank++;
            String nombre   = parts[1];
            String score    = parts[2];
            String cal      = parts[3];
            String prot     = parts[4];
            String gras     = parts[5];
            String carbs    = parts[6];
            String ingr     = parts[7];
            String pasosRaw = parts.length >= 9 ? parts[8] : "";

            // Extraer tag de categoría ontológica si presente
            String catTag = "";
            for (int fi = 9; fi < parts.length; fi++) {
                if (parts[fi].startsWith("cat:")) catTag = parts[fi].substring(4).trim();
            }
            if (catTag.isEmpty() && pasosRaw.contains("|cat:")) {
                int ci = pasosRaw.indexOf("|cat:");
                catTag  = pasosRaw.substring(ci + 5).trim();
                pasosRaw = pasosRaw.substring(0, ci);
            }

            sb.append("\n");
            sb.append(String.format("  #%-2d %-40s Score: %s%n", rank, nombre, score));
            sb.append("  ").append(rep(LIGHT, WIDTH - 2)).append("\n");
            sb.append("  Ingredientes:  ").append(ingr).append("\n");

            // Gap Analysis: ingredientes de la receta que el usuario no tiene
            Set<String> recipeTokens = new HashSet<>();
            for (String ing : ingr.split(";")) {
                for (String tok : ing.toLowerCase().split("\\s+")) {
                    tok = tok.trim();
                    if (!tok.isEmpty() && !SpanishStopWords.WORDS.contains(tok))
                        recipeTokens.add(tok);
                }
            }
            recipeTokens.removeAll(userTerms);
            if (!recipeTokens.isEmpty()) {
                List<String> gap = new ArrayList<>(recipeTokens);
                Collections.sort(gap);
                sb.append("  Te faltaría:   ")
                  .append(String.join(", ", gap.subList(0, Math.min(6, gap.size()))))
                  .append("\n");
            }

            if (!catTag.isEmpty()) {
                sb.append("  Categoría:     ").append(catTag).append("\n");
            }

            sb.append("\n  Macronutrientes:\n");
            sb.append("    Calorías: ").append(cal)
              .append(" kcal | Proteínas: ").append(prot).append(" g")
              .append(" | Grasas: ").append(gras).append(" g")
              .append(" | Carbohidratos: ").append(carbs).append(" g\n");

            sb.append("\n  Preparación:\n");
            String[] pasos = pasosRaw.split(";;", -1);
            for (int s = 0; s < Math.min(pasos.length, 5); s++) {
                String paso = pasos[s].trim();
                if (!paso.isEmpty()) sb.append(String.format("    %d. %s%n", s + 1, paso));
            }
            if (pasos.length > 5) sb.append("    ...\n");
        }

        sb.append("\n").append(rep(HEAVY, WIDTH)).append("\n");
        sb.append("  ").append(rank).append(" recetas encontradas\n");
        sb.append(rep(HEAVY, WIDTH)).append("\n\n");

        final String texto = sb.toString();
        System.out.print(texto);

        if (gui != null) {
            SwingUtilities.invokeLater(() -> gui.mostrarResultados(texto));
        }
    }

    private static String rep(String ch, int n) {
        StringBuilder sb = new StringBuilder(n * ch.length());
        for (int i = 0; i < n; i++) sb.append(ch);
        return sb.toString();
    }
}
