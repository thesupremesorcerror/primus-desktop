package com.primus.core.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineTest {

    private final PolicyEngine policyEngine = new PolicyEngine();

    @Test
    void lowRiskToolsAreAutoExecuted() {
        assertTrue(policyEngine.isAutoExecutionAllowed("get_metrics"));
        assertTrue(policyEngine.isAutoExecutionAllowed("get_logs"));
        assertTrue(policyEngine.isAutoExecutionAllowed("search_runbooks"));
        assertTrue(policyEngine.isAutoExecutionAllowed("search_incident_memory"));
    }

    @Test
    void mediumRiskToolsAreAutoExecuted() {
        assertTrue(policyEngine.isAutoExecutionAllowed("restart_service"));
        assertTrue(policyEngine.isAutoExecutionAllowed("scale_service"));
    }

    @Test
    void highRiskToolsAreBlocked() {
        assertFalse(policyEngine.isAutoExecutionAllowed("rollback_deployment"));
    }

    @Test
    void unknownToolDefaultsToBlocked() {
        assertFalse(policyEngine.isAutoExecutionAllowed("delete_database"));
    }

    @Test
    void riskLevelsAreCorrectlyReported() {
        assertEquals("LOW", policyEngine.getRiskLevel("get_metrics"));
        assertEquals("MEDIUM", policyEngine.getRiskLevel("restart_service"));
        assertEquals("HIGH", policyEngine.getRiskLevel("rollback_deployment"));
    }
}