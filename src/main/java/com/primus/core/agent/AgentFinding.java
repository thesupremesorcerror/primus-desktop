package com.primus.core.agent;

import java.util.List;

public record AgentFinding(
        String agent,
        double confidence,
        String summary,
        List<Evidence> evidence,
        List<String> toolsCalled
) {
    public record Evidence(String type, String detail, String source) {}
}
