package es.upm.agentlauncher;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

/**
 * Main: lanzador del sistema multiagente Nutrify.
 *
 * Crea el contenedor principal JADE e inicia los tres agentes en orden:
 *  1. AgenteInterfaz: debe registrarse en DF antes de que lleguen resultados
 *  2. AgenteInteligente:  carga el corpus (aprox 20k recetas) y se registra en DF
 *  3. AgentePercepcion: comienza la consulta una vez los otros estén listos
 *
 * Uso:
 *   Sin argumentos: modo interactivo (consola)
 *   Con argumentos: los tokens se pasan como ingredientes iniciales
 *                   Ejemplo: java Main tomate huevo cebolla
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // Garantizar modo con pantalla (no headless) para la GUI Swing
        System.setProperty("java.awt.headless", "false");

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("           NUTRIFY — Nutricionista Virtual JADE            ");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        Runtime rt = Runtime.instance();
        rt.setCloseVM(true);

        ProfileImpl profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "false");

        AgentContainer container = rt.createMainContainer(profile);

        // 0. Agente RMA (Interfaz gráfica de administración de JADE)
        try {
            container.createNewAgent("rma", "jade.tools.rma.rma", new Object[0]).start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 1. AgenteInterfaz: sin argumentos, recibe resultados pasivamente
        startAgent(container, "interfaz",
                "es.upm.nutricionista.AgenteInterfaz", null);
        Thread.sleep(800);

        // 2. AgenteInteligente: carga corpus + ontología + registra en DF
        startAgent(container, "cerebro",
                "es.upm.nutricionista.AgenteInteligente", null);
        Thread.sleep(1500);

        // 3. AgentePercepcion: inicia consulta (args opcionales)
        Object[] percepcionArgs = args.length > 0 ? args : null;
        startAgent(container, "percepcion",
                "es.upm.nutricionista.AgentePercepcion", percepcionArgs);

        System.out.println("[Main] Sistema Nutrify iniciado. Los tres agentes están en marcha.");
    }

    private static void startAgent(AgentContainer container, String name,
                                   String className, Object[] args)
            throws StaleProxyException {
        AgentController ac = container.createNewAgent(name, className, args);
        ac.start();
        System.out.println("[Main] Agente iniciado: " + name + " (" + className + ")");
    }
}
