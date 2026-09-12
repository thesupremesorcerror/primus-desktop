package com.primus.core.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFindingParserTest {

    @Test
    void parsesCleanJson() {
        String raw = """
            {"confidence": 0.85, "summary": "connection pool exhausted",
             "evidence": [{"type":"log_pattern","detail":"timeout errors","source":"get_logs"}]}
            """;

        AgentFinding finding = AgentFindingParser.parse("log_agent", List.of("get_logs"), raw);

        assertEquals("log_agent", finding.agent());
        assertEquals(0.85, finding.confidence());
        assertEquals("connection pool exhausted", finding.summary());
        assertEquals(1, finding.evidence().size());
        assertEquals("log_pattern", finding.evidence().get(0).type());
    }

    @Test
    void parsesJsonWithStrayTextAroundIt() {
        String raw = """
            Here is my analysis:
            {"confidence": 0.6, "summary": "possible deployment issue", "evidence": []}
            Let me know if you need more detail.
            """;

        AgentFinding finding = AgentFindingParser.parse("rag_agent", List.of(), raw);

        assertEquals(0.6, finding.confidence());
        assertEquals("possible deployment issue", finding.summary());
    }

    @Test
    void malformedJsonFailsSoftRatherThanThrowing() {
        String raw = "this is not JSON at all, no braces here";

        AgentFinding finding = AgentFindingParser.parse("metrics_agent", List.of("get_metrics"), raw);

        assertEquals("metrics_agent", finding.agent());
        assertEquals(0.0, finding.confidence());
        assertTrue(finding.summary().startsWith("Failed to parse"));
    }

    @Test
    void missingFieldsDefaultGracefully() {
        String raw = "{}";

        AgentFinding finding = AgentFindingParser.parse("diagnosis_agent", List.of(), raw);

        assertEquals(0.0, finding.confidence());
        assertEquals("", finding.summary());
    }
}