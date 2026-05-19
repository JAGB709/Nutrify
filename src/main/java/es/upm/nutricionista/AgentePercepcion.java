package es.upm.nutricionista;

import es.upm.nutricionista.nlp.TextNormalizer;
import es.upm.nutricionista.utils.DFHelper;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.List;
import java.util.Scanner;

/**
 * AgentePercepcion — puerta de entrada del pipeline Nutrify.
 *
 * Responsabilidades:
 *  1. Registrarse en el DF como "percepcion-service"
 *  2. Leer ingredientes del usuario (consola o argumentos de arranque)
 *  3. Normalizar la entrada a terminos limpios (Bag of Words + stemming)
 *  4. Descubrir al AgenteInteligente via DF y enviar ACLMessage.REQUEST
 *  5. Esperar respuesta ACLMessage.INFORM con receive() + block()
 *  6. Reenviar resultados al AgenteInterfaz
 *  7. Escuchar nuevas consultas desde la GUI (conversationId "nueva-consulta")
 */
public class AgentePercepcion extends Agent {

    private static final String SERVICE_TYPE      = "percepcion-service";
    private static final String RANKING_SERVICE   = "cerebro-service";
    private static final String DISPLAY_SERVICE   = "interfaz-service";
    private static final long   TIMEOUT_MS        = 15_000;

    @Override
    protected void setup() {
        System.out.println("[AgentePercepcion] Iniciando...");
        addBehaviour(new RegistrarServicioBehaviour());
        addBehaviour(new ConsultaBehaviour());
        addBehaviour(new EscucharNuevasConsultasBehaviour());
    }

    @Override
    protected void takeDown() {
        DFHelper.deregisterService(this);
        System.out.println("[AgentePercepcion] Detenido.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 1: RegistrarServicioBehaviour (OneShotBehaviour)
    // ─────────────────────────────────────────────────────────────────────────

    private class RegistrarServicioBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            DFHelper.registerService(myAgent, SERVICE_TYPE, "Nutrify-Percepcion");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 2: ConsultaBehaviour (CyclicBehaviour con dos estados)
    // ─────────────────────────────────────────────────────────────────────────

    private class ConsultaBehaviour extends CyclicBehaviour {
        private final Scanner scanner       = new Scanner(System.in);
        private boolean esperandoRespuesta  = false;
        private long    tiempoEnvio         = 0;
        private String  convId              = null;
        private String  queryContent        = null;

        @Override
        public void action() {
            if (!esperandoRespuesta) {
                // Estado 1: pedir ingredientes al usuario
                Object[] args = myAgent.getArguments();
                String rawInput;
                if (args != null && args.length > 0) {
                    rawInput = String.join(",", java.util.Arrays.stream(args)
                            .map(Object::toString).toArray(String[]::new));
                } else {
                    System.out.println("\n╔══════════════════════════════════════════════════════════╗");
                    System.out.println("  NUTRIFY — Introduce los ingredientes que tienes");
                    System.out.println("  (separados por coma, o 'salir' para terminar)");
                    System.out.println("╚══════════════════════════════════════════════════════════╝");
                    System.out.print("  > ");
                    rawInput = scanner.nextLine().trim();
                }

                if (rawInput.equalsIgnoreCase("salir") || rawInput.equalsIgnoreCase("exit")) {
                    System.out.println("[AgentePercepcion] Cerrando. ¡Hasta pronto!");
                    myAgent.doDelete();
                    return;
                }

                enviarConsulta(rawInput);

            } else {
                // Estado 2: esperar respuesta del AgenteInteligente
                if (System.currentTimeMillis() - tiempoEnvio > TIMEOUT_MS) {
                    System.err.println("[AgentePercepcion] Timeout: AgenteInteligente no respondio.");
                    esperandoRespuesta = false;
                    return;
                }
                MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId(convId)
                );
                ACLMessage resp = myAgent.receive(mt);
                if (resp == null) { block(); return; }

                reenviarAInterfaz(resp.getContent());
                esperandoRespuesta = false;
            }
        }

        private void enviarConsulta(String rawInput) {
            List<String> terms = TextNormalizer.normalize(rawInput);
            if (terms.isEmpty()) {
                System.out.println("[AgentePercepcion] No se reconocieron ingredientes. Intentalo de nuevo.");
                return;
            }
            queryContent = String.join(",", terms);
            System.out.println("[AgentePercepcion] Terminos normalizados: " + terms);

            AID cerebro = DFHelper.searchService(myAgent, RANKING_SERVICE);
            if (cerebro == null) {
                System.err.println("[AgentePercepcion] AgenteInteligente no disponible aun. Reintenta en breve.");
                return;
            }

            convId = "nutrify-" + System.currentTimeMillis();
            ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
            req.addReceiver(cerebro);
            req.setContent(queryContent);
            req.setConversationId(convId);
            myAgent.send(req);
            System.out.println("[AgentePercepcion] Consulta enviada: " + queryContent);

            tiempoEnvio        = System.currentTimeMillis();
            esperandoRespuesta = true;
        }

        private void reenviarAInterfaz(String contenido) {
            AID interfaz = DFHelper.searchService(myAgent, DISPLAY_SERVICE);
            if (interfaz == null) {
                System.err.println("[AgentePercepcion] AgenteInterfaz no disponible.");
                return;
            }
            ACLMessage display = new ACLMessage(ACLMessage.INFORM);
            display.addReceiver(interfaz);
            display.setContent(queryContent + "||" + contenido);
            myAgent.send(display);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behaviour 3: EscucharNuevasConsultasBehaviour (CyclicBehaviour)
    // Recibe INFORM con conversationId "nueva-consulta" desde la GUI
    // ─────────────────────────────────────────────────────────────────────────

    private class EscucharNuevasConsultasBehaviour extends CyclicBehaviour {
        private final MessageTemplate mt = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
            MessageTemplate.MatchConversationId("nueva-consulta")
        );

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if (msg == null) { block(); return; }

            String rawInput = msg.getContent();
            if (rawInput == null || rawInput.trim().isEmpty()) return;

            System.out.println("[AgentePercepcion] Nueva consulta desde GUI: " + rawInput);

            List<String> terms = TextNormalizer.normalize(rawInput);
            if (terms.isEmpty()) return;

            String queryContent = String.join(",", terms);
            AID cerebro = DFHelper.searchService(myAgent, RANKING_SERVICE);
            if (cerebro == null) { System.err.println("[AgentePercepcion] Cerebro no disponible."); return; }

            String convId = "nutrify-gui-" + System.currentTimeMillis();
            ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
            req.addReceiver(cerebro);
            req.setContent(queryContent);
            req.setConversationId(convId);
            myAgent.send(req);

            // Esperar respuesta y reenviar a interfaz
            MessageTemplate respMt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId(convId)
            );
            ACLMessage resp = myAgent.blockingReceive(respMt, 15_000);
            if (resp == null) { System.err.println("[AgentePercepcion] Timeout esperando respuesta de cerebro."); return; }

            AID interfaz = DFHelper.searchService(myAgent, DISPLAY_SERVICE);
            if (interfaz == null) { System.err.println("[AgentePercepcion] Interfaz no disponible."); return; }

            ACLMessage display = new ACLMessage(ACLMessage.INFORM);
            display.addReceiver(interfaz);
            display.setContent(queryContent + "||" + resp.getContent());
            myAgent.send(display);
        }
    }
}
