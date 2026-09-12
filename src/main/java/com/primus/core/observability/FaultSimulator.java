package com.primus.core.observability;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FaultSimulator {

    private static final FaultType[] INJECTABLE_FAULTS = {
            FaultType.DEPLOYMENT_ISSUE, FaultType.CONNECTION_POOL_EXHAUSTION, FaultType.TRANSIENT_GLITCH
    };

    private final Map<String, FaultType> serviceFaults = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public boolean isHealthy(String serviceName) {
        return getFaultType(serviceName) == FaultType.NONE;
    }

    public FaultType getFaultType(String serviceName) {
        return serviceFaults.getOrDefault(serviceName, FaultType.NONE);
    }

    /** Kills a service with a specific fault type. Passing null assigns a random one. */
    public FaultType kill(String serviceName, FaultType faultType) {
        FaultType assigned = faultType == null ? INJECTABLE_FAULTS[random.nextInt(INJECTABLE_FAULTS.length)] : faultType;
        serviceFaults.put(serviceName, assigned);
        return assigned;
    }

    /** Kills a service with a randomly chosen fault type. Returns the type assigned, so callers can capture ground truth. */
    public FaultType kill(String serviceName) {
        return kill(serviceName, null);
    }

    public void heal(String serviceName) {
        serviceFaults.put(serviceName, FaultType.NONE);
    }

    /**
     * Attempts to remediate a service with an action that fixes a specific fault type.
     * Only actually heals if the action matches the injected fault. Returns true if
     * the service ends up healthy (either because this action fixed it, or it was
     * already healthy).
     */
    public boolean attemptHeal(String serviceName, FaultType actionFixes) {
        FaultType current = getFaultType(serviceName);
        if (current == FaultType.NONE) return true;
        if (current == actionFixes) {
            heal(serviceName);
            return true;
        }
        return false;
    }

    /** @deprecated kept for backward compatibility; true heals, false kills with a random fault type. */
    @Deprecated
    public void setHealthy(String serviceName, boolean healthy) {
        if (healthy) heal(serviceName);
        else kill(serviceName);
    }
}