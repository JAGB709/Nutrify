package es.upm.nutricionista;

import es.upm.nutricionista.nlp.SpanishStopWords;
import es.upm.nutricionista.nlp.TextNormalizer;
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
 *  2. Recibir ACLMessage.INFORM de AgentePercepcion con los resultados
 *  3. Mostrar resultados IR y recetas de temporada en consola y GUI Swing con formato HTML
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

        SwingUtilities.invokeLater(() -> {
            try {
                gui = new NutrifyGUI();
                gui.setListener(ingredientes -> {
                    addBehaviour(new OneShotBehaviour() {
                        @Override
                        public void action() {
                            AID percepcion = DFHelper.searchService(
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
                gui.toFront();
                gui.requestFocus();
                System.out.println("[AgenteInterfaz] Ventana GUI abierta.");
            } catch (Exception ex) {
                System.err.println("[AgenteInterfaz] No se pudo abrir la GUI: " + ex);
                ex.printStackTrace();
            }
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
    // Usa receive() + block() — patrón correcto JADE para CyclicBehaviour
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
            if (gui != null) {
                final String html = htmlPage("<p><font color='red'><b>Error:</b> "
                        + esc(content.substring(6)) + "</font></p>");
                SwingUtilities.invokeLater(() -> gui.mostrarResultados(html));
            }
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

        // Separar resultados IR de recetas de temporada (prefijo "TEMPORAL:")
        List<String[]> results = new ArrayList<>();
        List<String[]> temporalResults = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("TEMPORAL:")) {
                String[] parts = line.substring(9).split("\\|", -1);
                if (parts.length >= 8) temporalResults.add(parts);
            } else {
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 8) results.add(parts);
            }
        }

        // Salida consola
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(rep(HEAVY, WIDTH)).append("\n");
        sb.append("  NUTRIFY — Resultados para: ").append(query).append("\n");
        sb.append(rep(HEAVY, WIDTH)).append("\n");

        if (results.isEmpty() && temporalResults.isEmpty()) {
            sb.append("\n  No se encontraron recetas con esos ingredientes.\n\n");
            sb.append(rep(HEAVY, WIDTH)).append("\n");
            System.out.print(sb);
            if (gui != null) {
                final String htmlVacio = generarHtml(query, results, temporalResults, userTerms);
                SwingUtilities.invokeLater(() -> gui.mostrarResultados(htmlVacio));
            }
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

            String catTag = "";
            for (int fi = 9; fi < parts.length; fi++) {
                if (parts[fi].startsWith("cat:")) catTag = parts[fi].substring(4).trim();
            }
            if (catTag.isEmpty() && pasosRaw.contains("|cat:")) {
                int ci = pasosRaw.indexOf("|cat:");
                catTag   = pasosRaw.substring(ci + 5).trim();
                pasosRaw = pasosRaw.substring(0, ci);
            }

            sb.append("\n");
            sb.append(String.format("  #%-2d %-40s Score: %s%n", rank, nombre, score));
            sb.append("  ").append(rep(LIGHT, WIDTH - 2)).append("\n");
            sb.append("  Ingredientes:  ").append(ingr).append("\n");

            List<String> gap = calcularGap(ingr, userTerms);
            Collections.sort(gap);
            if (!gap.isEmpty()) {
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

        // Recetas de temporada en consola
        if (!temporalResults.isEmpty()) {
            sb.append("\n  ").append(rep(LIGHT, WIDTH - 2)).append("\n");
            sb.append("  RECETAS DE TEMPORADA\n");
            sb.append("  ").append(rep(LIGHT, WIDTH - 2)).append("\n");
            for (String[] parts : temporalResults) {
                rank++;
                String nombre   = parts[1];
                String ingr     = parts[7];
                String pasosRaw = parts.length >= 9 ? parts[8] : "";

                sb.append("\n");
                sb.append(String.format("  #%-2d [Temporada] %s%n", rank, nombre));
                sb.append("  Ingredientes:  ").append(ingr).append("\n");

                List<String> gap = calcularGap(ingr, userTerms);
                Collections.sort(gap);
                if (!gap.isEmpty()) {
                    sb.append("  Te faltaría:   ")
                      .append(String.join(", ", gap.subList(0, Math.min(6, gap.size()))))
                      .append("\n");
                }

                sb.append("\n  Preparación:\n");
                String[] pasos = pasosRaw.split(";;", -1);
                for (int s = 0; s < Math.min(pasos.length, 3); s++) {
                    String paso = pasos[s].trim();
                    if (!paso.isEmpty()) sb.append(String.format("    %d. %s%n", s + 1, paso));
                }
                if (pasos.length > 3) sb.append("    ...\n");
            }
        }

        sb.append("\n").append(rep(HEAVY, WIDTH)).append("\n");
        sb.append("  ").append(rank).append(" recetas encontradas");
        if (!temporalResults.isEmpty()) {
            sb.append(" (").append(temporalResults.size()).append(" de temporada)");
        }
        sb.append("\n");
        sb.append(rep(HEAVY, WIDTH)).append("\n\n");

        final String textoConsola = sb.toString();
        System.out.print(textoConsola);

        if (gui != null) {
            final String htmlGuiContent = generarHtml(query, results, temporalResults, userTerms);
            SwingUtilities.invokeLater(() -> gui.mostrarResultados(htmlGuiContent));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gap Analysis — nivel de ingrediente completo
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gap Analysis corregido: trabaja a nivel de ingrediente completo.
     * "pimienta negra" se marca como faltante SOLO si ni "pimienta" ni "negra"
     * están en userTerms (tras normalización/stemming).
     */
    private List<String> calcularGap(String ingr, Set<String> userTerms) {
        List<String> missing = new ArrayList<>();
        for (String ing : ingr.split(";")) {
            String ingClean = ing.toLowerCase().trim();
            if (ingClean.isEmpty()) continue;

            String[] ingTokens = ingClean.split("\\s+");

            boolean match = false;
            for (String tok : ingTokens) {
                if (tok.isEmpty()) continue;
                List<String> normalized = TextNormalizer.normalize(tok);
                for (String stemmed : normalized) {
                    if (userTerms.contains(stemmed)) { match = true; break; }
                    // Coincidencia por prefijo cercano (≤2 chars): resuelve inconsistencias del stemmer
                    if (stemmed.length() >= 4) {
                        for (String ut : userTerms) {
                            if ((ut.startsWith(stemmed) && ut.length() - stemmed.length() <= 2)
                             || (stemmed.startsWith(ut) && stemmed.length() - ut.length() <= 2)) {
                                match = true; break;
                            }
                        }
                    }
                    if (match) break;
                }
                if (match) break;
            }

            if (!match) {
                StringBuilder display = new StringBuilder();
                for (String tok : ingTokens) {
                    tok = tok.trim();
                    if (!tok.isEmpty() && !SpanishStopWords.WORDS.contains(tok)) {
                        if (display.length() > 0) display.append(" ");
                        display.append(tok);
                    }
                }
                if (display.length() > 0) missing.add(display.toString());
            }
        }
        return missing;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generación de HTML para la GUI
    // ─────────────────────────────────────────────────────────────────────────

    private String generarHtml(String query, List<String[]> results,
                                List<String[]> temporalResults, Set<String> userTerms) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' bgcolor='#2E7D32' cellpadding='10'><tr><td>")
          .append("<font color='white' size='+1'><b>NUTRIFY</b></font>")
          .append(" &mdash; <font color='#C8E6C9'><i>").append(esc(query)).append("</i></font>")
          .append("</td></tr></table><br>\n");

        if (results.isEmpty() && temporalResults.isEmpty()) {
            sb.append("<p><i>No se encontraron recetas con esos ingredientes.</i></p>");
        } else {
            for (int i = 0; i < results.size(); i++) {
                sb.append(tarjetaHtml(i + 1, results.get(i), userTerms)).append("<br>\n");
            }
            if (!temporalResults.isEmpty()) {
                sb.append("<table width='100%' bgcolor='#1B5E20' cellpadding='8'><tr><td>")
                  .append("<font color='white'><b>Recetas de Temporada</b></font>")
                  .append("</td></tr></table><br>\n");
                for (int i = 0; i < temporalResults.size(); i++) {
                    sb.append(tarjetaTemporalHtml(results.size() + i + 1,
                            temporalResults.get(i), userTerms)).append("<br>\n");
                }
            }
            sb.append("<p align='right'><font color='gray' size='-1'>")
              .append(results.size() + temporalResults.size())
              .append(" recetas encontradas");
            if (!temporalResults.isEmpty()) {
                sb.append(" (").append(temporalResults.size()).append(" de temporada)");
            }
            sb.append("</font></p>");
        }
        return htmlPage(sb.toString());
    }

    private String tarjetaHtml(int rank, String[] parts, Set<String> userTerms) {
        String nombre   = parts[1];
        String score    = parts[2];
        String cal      = parts[3];
        String prot     = parts[4];
        String gras     = parts[5];
        String carbs    = parts[6];
        String ingr     = parts[7];
        String pasosRaw = parts.length >= 9 ? parts[8] : "";

        String catTag = "";
        for (int fi = 9; fi < parts.length; fi++) {
            if (parts[fi].startsWith("cat:")) catTag = parts[fi].substring(4).trim();
        }
        if (catTag.isEmpty() && pasosRaw.contains("|cat:")) {
            int ci = pasosRaw.indexOf("|cat:");
            catTag   = pasosRaw.substring(ci + 5).trim();
            pasosRaw = pasosRaw.substring(0, ci);
        }

        String pctStr; String scoreBg;
        try {
            double sc = Double.parseDouble(score);
            pctStr   = String.format("%.0f%%", sc * 100.0);
            scoreBg  = sc >= 0.6 ? "#E8F5E9" : (sc >= 0.3 ? "#FFF8E1" : "#FFEBEE");
        } catch (NumberFormatException e) {
            pctStr = score; scoreBg = "#E8F5E9";
        }

        List<String> gap = calcularGap(ingr, userTerms);
        Collections.sort(gap);

        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' bgcolor='white' border='1' bordercolor='#dddddd'"
                + " cellpadding='10' cellspacing='0'>\n");

        sb.append("<tr bgcolor='#FAFAFA'>")
          .append("<td width='80%'><font color='#1565C0' size='+1'><b>#")
          .append(rank).append(" ").append(esc(nombre)).append("</b></font></td>")
          .append("<td bgcolor='").append(scoreBg).append("' align='center'>"
                + "<font size='+1'><b>").append(pctStr).append("</b></font><br>"
                + "<font size='-1' color='gray'>relevancia</font></td>")
          .append("</tr>\n");

        sb.append("<tr><td colspan='2'>")
          .append("<b>Ingredientes:</b> ").append(esc(ingr).replace(";", " \u00B7 ")).append("<br>");
        if (!gap.isEmpty()) {
            sb.append("<font color='#E65100'><b>Te faltar&iacute;a:</b> ")
              .append(esc(String.join(", ", gap.subList(0, Math.min(6, gap.size())))))
              .append("</font><br>");
        }
        if (!catTag.isEmpty()) {
            sb.append("<font color='gray'><b>Categor&iacute;a:</b> ").append(esc(catTag))
              .append("</font>");
        }
        sb.append("</td></tr>\n");

        // Fila macronutrientes — muestra N/D si todos son 0
        boolean hasMacros = !"0".equals(cal) || !"0".equals(prot)
                || !"0".equals(gras) || !"0".equals(carbs);
        sb.append("<tr bgcolor='#F5F5F5'><td colspan='2'>");
        if (hasMacros) {
            sb.append("<table width='100%'><tr>")
              .append("<td align='center'><b>").append(cal).append("</b><br>"
                    + "<font size='-1' color='gray'>kcal</font></td>")
              .append("<td align='center'><b>").append(prot).append("g</b><br>"
                    + "<font size='-1' color='gray'>prot.</font></td>")
              .append("<td align='center'><b>").append(gras).append("g</b><br>"
                    + "<font size='-1' color='gray'>grasa</font></td>")
              .append("<td align='center'><b>").append(carbs).append("g</b><br>"
                    + "<font size='-1' color='gray'>carbs</font></td>")
              .append("</tr></table>");
        } else {
            sb.append("<font color='gray' size='-1'><i>"
                    + "Informaci&oacute;n nutricional no disponible</i></font>");
        }
        sb.append("</td></tr>\n");

        String[] pasos = pasosRaw.split(";;", -1);
        List<String> pasosValidos = new ArrayList<>();
        for (String p : pasos) { if (!p.trim().isEmpty()) pasosValidos.add(p.trim()); }

        if (!pasosValidos.isEmpty()) {
            sb.append("<tr><td colspan='2'><b>Preparaci&oacute;n:</b>");
            if (pasosValidos.size() == 1 && pasosValidos.get(0).length() > 120) {
                sb.append("<p style='margin:6px 0'>").append(esc(pasosValidos.get(0)))
                  .append("</p>");
            } else {
                sb.append("<ol>");
                for (String paso : pasosValidos) {
                    sb.append("<li>").append(esc(paso)).append("</li>");
                }
                sb.append("</ol>");
            }
            sb.append("</td></tr>\n");
        }

        sb.append("</table>\n");
        return sb.toString();
    }

    private String tarjetaTemporalHtml(int rank, String[] parts, Set<String> userTerms) {
        String nombre   = parts[1];
        String ingr     = parts[7];
        String pasosRaw = parts.length >= 9 ? parts[8] : "";

        List<String> gap = calcularGap(ingr, userTerms);
        Collections.sort(gap);

        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' bgcolor='white' border='1' bordercolor='#a5d6a7'"
                + " cellpadding='10' cellspacing='0'>\n");

        sb.append("<tr bgcolor='#E8F5E9'>")
          .append("<td width='80%'><font color='#1B5E20' size='+1'><b>#")
          .append(rank).append(" ").append(esc(nombre)).append("</b></font></td>")
          .append("<td bgcolor='#C8E6C9' align='center'>")
          .append("<font color='#1B5E20'><b>Receta de<br>temporada</b></font></td>")
          .append("</tr>\n");

        sb.append("<tr><td colspan='2'>")
          .append("<b>Ingredientes:</b> ").append(esc(ingr).replace(";", " \u00B7 ")).append("<br>");
        if (!gap.isEmpty()) {
            sb.append("<font color='#E65100'><b>Te faltar&iacute;a:</b> ")
              .append(esc(String.join(", ", gap.subList(0, Math.min(6, gap.size())))))
              .append("</font>");
        }
        sb.append("</td></tr>\n");

        sb.append("<tr bgcolor='#F5F5F5'><td colspan='2'>")
          .append("<font color='gray' size='-1'><i>"
                + "Informaci&oacute;n nutricional no disponible</i></font>")
          .append("</td></tr>\n");

        String[] pasos = pasosRaw.split(";;", -1);
        List<String> pasosValidos = new ArrayList<>();
        for (String p : pasos) { if (!p.trim().isEmpty()) pasosValidos.add(p.trim()); }

        if (!pasosValidos.isEmpty()) {
            sb.append("<tr><td colspan='2'><b>Preparaci&oacute;n:</b>");
            if (pasosValidos.size() == 1 && pasosValidos.get(0).length() > 120) {
                sb.append("<p style='margin:6px 0'>").append(esc(pasosValidos.get(0)))
                  .append("</p>");
            } else {
                sb.append("<ol>");
                for (String paso : pasosValidos) {
                    sb.append("<li>").append(esc(paso)).append("</li>");
                }
                sb.append("</ol>");
            }
            sb.append("</td></tr>\n");
        }

        sb.append("</table>\n");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String htmlPage(String body) {
        return "<html><body bgcolor='#f8f8f8'"
             + " style='font-family:Arial,sans-serif;font-size:13px;margin:10px;'>"
             + body + "</body></html>";
    }

    private static String rep(String ch, int n) {
        StringBuilder sb = new StringBuilder(n * ch.length());
        for (int i = 0; i < n; i++) sb.append(ch);
        return sb.toString();
    }
}
