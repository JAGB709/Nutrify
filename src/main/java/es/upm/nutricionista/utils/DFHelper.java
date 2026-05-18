package es.upm.nutricionista.utils;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

/**
 * Static helper for JADE Directory Facilitator operations.
 * All agents use this to register and discover services.
 */
public class DFHelper {

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

    public static void deregisterService(Agent agent) {
        try {
            DFService.deregister(agent);
        } catch (FIPAException e) {
            System.err.println("[DFHelper] Error al desregistrar " + agent.getLocalName() + ": " + e.getMessage());
        }
    }
}
