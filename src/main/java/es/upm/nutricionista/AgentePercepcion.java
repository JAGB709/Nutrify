package es.upm.nutricionista;

import es.upm.nutricionista.modelo.Recipe;
import es.upm.nutricionista.nlp.TextNormalizer;
import es.upm.nutricionista.utils.DFHelper;
import es.upm.nutricionista.utils.SeasonalRecipeFetcher;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AgentePercepcion: percepción del entorno en el pipeline Nutrify.
 *
 * Responsabilidades:
 *  1. Registrarse en el DF como "percepcion-service"
 *  2. Percibir la entrada del usuario (consola o GUI) y normalizarla (NLP)
 *  3. Descubrir al AgenteInteligente via DF y enviar ACLMessage.REQUEST
 *  4. En paralelo con la espera de resultados IR, consultar TheMealDB para
 *     obtener 2 recetas de temporada (percepción ambiental dinámica)
 *  5. Combinar resultados IR + recetas de temporada y reenviar a AgenteInterfaz
 */
public class AgentePercepcion extends Agent {

    private static final String SERVICE_TYPE    = "percepcion-service";
    private static final String RANKING_SERVICE = "cerebro-service";
    private static final String DISPLAY_SERVICE = "interfaz-service";
    private static final long   TIMEOUT_MS      = 15_000;

    @Override
    protected void setup() {
        System.out.println("[AgentePercepcion] Iniciando...");
        addBehaviour(new RegistrarServicioBehaviour());

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            // Modo batch: primer query desde argumentos de arranque
            String rawInput = String.join(",", Arrays.stream(args)
                    .map(Object::toString).toArray(String[]::new));
            addBehaviour(new IniciarConArgsBehaviour(rawInput));
        } else {
            // Modo interactivo: lector de consola en hilo daemon
            iniciarLectorConsola();
        }

        // Behaviour principal: gestiona consultas de consola Y de la GUI
        addBehaviour(new GestionarConsultasBehaviour());
    }

    @Override
    protected void takeDown() {
        DFHelper.deregisterService(this);
        System.out.println("[AgentePercepcion] Detenido.");
    }

    /**
     * Serializa una lista de recetas de temporada al formato TEMPORAL: para AgenteInterfaz.
     * Formato por línea: TEMPORAL:<id>|<nombre>|0.0000|0|0|0|0|<ingredientes>|<pasos>
     */
    private String serializarTemporales(List<Recipe> recetas) {
        StringBuilder sb = new StringBuilder();
        for (Recipe r : recetas) {
            List<String> cleanIngs = new java.util.ArrayList<>();
            for (String ing : r.getIngredientes())
                cleanIngs.add(ing.replace(";", ",").replace("|", "##"));
            List<String> cleanPasos = new java.util.ArrayList<>();
            for (String p : r.getPasos())
                cleanPasos.add(p.replace(";;", " ").replace("|", "##"));

            sb.append("TEMPORAL:");
            sb.append(r.getId()).append("|");
            sb.append(r.getNombre().replace("|", "##")).append("|");
            sb.append("0.0000|0|0|0|0|");
            sb.append(String.join(";", cleanIngs)).append("|");
            sb.append(String.join(";;", cleanPasos));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Hilo daemon que lee System.in y envía INFORM a sí mismo.
     * Al no correr en el hilo JADE, no bloquea el scheduler de behaviours.
     */
    private void iniciarLectorConsola() {
        Thread lector = new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            System.out.println("\n╔══════════════════════════════════════════════════════════╗");
            System.out.println("  NUTRIFY — Introduce los ingredientes que tienes");
            System.out.println("  (separados por coma, o 'salir' para terminar)");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            System.out.print("  > ");
            while (sc.hasNextLine()) {
                String linea = sc.nextLine().trim();
                if (linea.equalsIgnoreCase("salir") || linea.equalsIgnoreCase("exit")) {
                    System.out.println("[AgentePercepcion] Cerrando. ¡Hasta pronto!");
                    doDelete();
                    return;
                }
                if (!linea.isEmpty()) {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(getAID());
                    msg.setConversationId("console-input");
                    msg.setContent(linea);
                    send(msg);
                }
                System.out.print("  > ");
            }
        }, "Nutrify-Console");
        lector.setDaemon(true);
        lector.start();
    }

    
    // Comportamiento 1: Registro en DF
    

    private class RegistrarServicioBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            DFHelper.registerService(myAgent, SERVICE_TYPE, "Nutrify-Percepcion");
        }
    }

    
    // Comportamiento 2: Primer query desde args (modo batch)
    

    private class IniciarConArgsBehaviour extends OneShotBehaviour {
        private final String rawInput;
        IniciarConArgsBehaviour(String raw) { this.rawInput = raw; }

        @Override
        public void action() {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(myAgent.getAID());
            msg.setConversationId("console-input");
            msg.setContent(rawInput);
            myAgent.send(msg);
        }
    }

    
    // Comportamiento 3: GestionarConsultasBehaviour
    // Recibe consultas de consola ("console-input") y de la GUI ("nueva-consulta")
    

    private class GestionarConsultasBehaviour extends CyclicBehaviour {

        // Acepta mensajes de consola o de la GUI
        private final MessageTemplate MT_QUERY = MessageTemplate.or(
            MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId("console-input")),
            MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId("nueva-consulta")));

        @Override
        public void action() {
            ACLMessage query = myAgent.receive(MT_QUERY);
            if (query == null) { block(); return; }

            String rawInput = query.getContent();
            if (rawInput == null || rawInput.trim().isEmpty()) return;

            List<String> terms = TextNormalizer.normalize(rawInput);
            if (terms.isEmpty()) {
                System.out.println("[AgentePercepcion] No se reconocieron ingredientes.");
                return;
            }
            String queryContent = String.join(",", terms);
            System.out.println("[AgentePercepcion] Términos normalizados: " + terms);

            AID cerebro = DFHelper.searchService(myAgent, RANKING_SERVICE);
            if (cerebro == null) {
                System.err.println("[AgentePercepcion] AgenteInteligente no disponible.");
                return;
            }

            String convId = "nutrify-" + System.currentTimeMillis();
            ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
            req.addReceiver(cerebro);
            req.setContent(queryContent);
            req.setConversationId(convId);
            myAgent.send(req);
            System.out.println("[AgentePercepcion] Consulta IR enviada: " + queryContent);

            // Lanzar la búsqueda de recetas de temporada en paralelo con la espera de IR
            AtomicReference<List<Recipe>> temporalRef =
                    new AtomicReference<>(Collections.emptyList());
            Thread seasonThread = new Thread(() -> {
                try {
                    temporalRef.set(SeasonalRecipeFetcher.fetch(2));
                } catch (Exception e) {
                    System.err.println("[AgentePercepcion] Error recetas temporada: "
                            + e.getMessage());
                }
            }, "Nutrify-SeasonalFetch");
            seasonThread.setDaemon(true);
            seasonThread.start();

            // Esperar respuesta del AgenteInteligente
            MessageTemplate respMt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId(convId));
            ACLMessage resp = myAgent.blockingReceive(respMt, TIMEOUT_MS);
            if (resp == null) {
                System.err.println("[AgentePercepcion] Timeout esperando respuesta de cerebro.");
                return;
            }

            // Esperar a que el hilo de temporada termine (máx. 10 s adicionales)
            try { seasonThread.join(10_000); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            List<Recipe> temporal = temporalRef.get();

            // Reenviar resultados al AgenteInterfaz (IR + temporada)
            AID interfaz = DFHelper.searchService(myAgent, DISPLAY_SERVICE);
            if (interfaz == null) {
                System.err.println("[AgentePercepcion] AgenteInterfaz no disponible.");
                return;
            }
            String irContent  = resp.getContent();
            String tempSerial = serializarTemporales(temporal);
            String combined   = tempSerial.isEmpty() ? irContent
                    : irContent + (irContent.endsWith("\n") ? "" : "\n") + tempSerial.trim();

            ACLMessage display = new ACLMessage(ACLMessage.INFORM);
            display.addReceiver(interfaz);
            display.setContent(queryContent + "||" + combined);
            myAgent.send(display);
        }
    }
}
