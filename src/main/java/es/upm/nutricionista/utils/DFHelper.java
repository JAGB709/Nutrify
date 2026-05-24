package es.upm.nutricionista.utils;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

/**
 * Utilidad para registrar, buscar y desregistrar servicios en el Directorio de Facilitadores (DF) de JADE.
 * Proporciona métodos estáticos para que los agentes puedan interactuar con el DF de manera sencilla, 
 * facilitando la gestión de servicios ofrecidos y consumidos por los agentes en el sistema.
 */
public class DFHelper {

    /**
     * Registra un servicio en el DF con el tipo y nombre especificados.
     *
     * @param agent       el agente que ofrece el servicio
     * @param serviceType el tipo de servicio (e.g., "RecipeSearch", "NutrientInfo")
     * @param serviceName el nombre del servicio (e.g., "RecipeSearchService")
     */
    public static void registerService(Agent agent, String serviceType, String serviceName) {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(agent.getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(serviceName);
        dfd.addServices(sd);
        try {
            DFService.register(agent, dfd);
            System.out.println("[DFHelper] " + agent.getLocalName() + " registrado: " + serviceType);
        } catch (FIPAException e) {
            System.err.println("[DFHelper] Error al registrar " + agent.getLocalName() + ": " + e.getMessage());
        }
    }

    /**
     * Busca un servicio en el DF por su tipo y devuelve el AID del agente que lo ofrece.
     *
     * @param agent       el agente que realiza la búsqueda
     * @param serviceType el tipo de servicio a buscar
     * @return el AID del agente que ofrece el servicio, o null si no se encuentra
     */
    public static AID searchService(Agent agent, String serviceType) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        template.addServices(sd);
        try {
            DFAgentDescription[] results = DFService.search(agent, template);
            if (results != null && results.length > 0) {
                return results[0].getName();
            }
        } catch (FIPAException e) {
            System.err.println("[DFHelper] Error al buscar '" + serviceType + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Desregistra el agente del DF, eliminando todos los servicios que ofrecía.
     *
     * @param agent el agente que se desregistrará
     */
    public static void deregisterService(Agent agent) {
        try {
            DFService.deregister(agent);
        } catch (FIPAException e) {
            System.err.println("[DFHelper] Error al desregistrar " + agent.getLocalName() + ": " + e.getMessage());
        }
    }
}
