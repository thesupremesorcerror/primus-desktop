package com.primus.core.observability;

import com.primus.core.incident.Incident;
import com.primus.core.incident.IncidentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class IncidentDetectionJob {

    private static final List<String> MONITORED_SERVICES = List.of("payment-api", "auth-api", "checkout-api");

    private final FaultSimulator faultSimulator;
    private final IncidentRepository incidentRepository;

    public IncidentDetectionJob(FaultSimulator faultSimulator, IncidentRepository incidentRepository) {
        this.faultSimulator = faultSimulator;
        this.incidentRepository = incidentRepository;
    }

    @Scheduled(fixedRate = 5000) // check every 5 seconds
    public void checkServiceHealth() {
        for (String service : MONITORED_SERVICES) {
            boolean healthy = faultSimulator.isHealthy(service);
            Incident openIncident = findOpenIncidentFor(service);

            if (!healthy && openIncident == null) {
                createIncident(service);
            } else if (healthy && openIncident != null) {
                resolveIncident(openIncident);
            }
        }
    }

    private Incident findOpenIncidentFor(String service) {
        return incidentRepository.findAll().stream()
                .filter(i -> i.getAffectedService().equals(service))
                .filter(i -> !i.getStatus().equals("RESOLVED"))
                .findFirst()
                .orElse(null);
    }

    private void createIncident(String service) {
        Incident incident = new Incident(
                service + " is not responding",
                "HIGH",
                "OPEN",
                service
        );
        incidentRepository.save(incident);
        System.out.println("[DETECTION] Incident created for " + service);
    }

    private void resolveIncident(Incident incident) {
        incident.setStatus("RESOLVED");
        incident.setResolvedAt(Instant.now());
        incidentRepository.save(incident);
        System.out.println("[DETECTION] Incident resolved for " + incident.getAffectedService());
    }
}