package com.primus.core.observability;

public enum FaultType {
    NONE,
    DEPLOYMENT_ISSUE,
    CONNECTION_POOL_EXHAUSTION,
    TRANSIENT_GLITCH
}
