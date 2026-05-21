package es.upm.nutricionista;

import es.upm.nutricionista.nlp.TextNormalizer;
import es.upm.nutricionista.utils.DFHelper;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * AgentePercepcion — puerta de entrada del pipeline Nutrify.
 *
 * Responsabilidades:
 *  1. Registrarse en el DF como "percepcion-service"
 *  2. Leer ingredientes del usuario:
 *       - Modo consola: hilo daemon separado (no bloquea el hilo JADE)
 *       - Modo GUI:     recibe ACLMessage INFORM con convId "nueva-consulta"
 *  3. Normalizar la entrada a términos limpios (Bag of Words + stemming)
 *  4. Descubrir al AgenteInteligente via DF y enviar ACLMessage.REQUEST
 *  5. Esperar respuesta y reenviar al AgenteInterfaz
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

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 1: Registro en DF
    // ─────────────────────────────────────────────────────────────────────────

    private class RegistrarServicioBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            DFHelper.registerService(myAgent, SERVICE_TYPE, "Nutrify-Percepcion");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 2: Primer query desde args (modo batch)
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 3: GestionarConsultasBehaviour
    // Recibe consultas de consola ("console-input") y de la GUI ("nueva-consulta")
    // ─────────────────────────────────────────────────────────────────────────

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
            System.out.println("[AgentePercepcion] Consulta enviada: " + queryContent);

            // Esperar respuesta del AgenteInteligente (blockingReceive es aceptable
            // aquí porque es la única operación de este behaviour)
            MessageTemplate respMt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId(convId));
            ACLMessage resp = myAgent.blockingReceive(respMt, TIMEOUT_MS);
            if (resp == null) {
                System.err.println("[AgentePercepcion] Timeout esperando respuesta de cerebro.");
                return;
            }

            // Reenviar resultados al AgenteInterfaz
            AID interfaz = DFHelper.searchService(myAgent, DISPLAY_SERVICE);
            if (interfaz == null) {
                System.err.println("[AgentePercepcion] AgenteInterfaz no disponible.");
                return;
            }
            ACLMessage display = new ACLMessage(ACLMessage.INFORM);
            display.addReceiver(interfaz);
            display.setContent(queryContent + "||" + resp.getContent());
            myAgent.send(display);
        }
    }
}
