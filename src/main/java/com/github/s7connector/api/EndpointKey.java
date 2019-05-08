package com.github.s7connector.api;

import java.time.Duration;

public final class EndpointKey {
    private final String host;
    private final SiemensPLCS plcType;
    private final int rack;
    private final int slot;
    private final int port;
    private final Duration period;

    public EndpointKey(String host, SiemensPLCS plcType, int rack, int slot, int port) {
        this.host = host;
        this.plcType = plcType;
        this.rack = rack;
        this.slot = slot;
        this.port = port;
        period = Duration.ofMillis(500);
    }

    public EndpointKey(String host, SiemensPLCS plcType, int rack, int slot, int port, Duration timeout, Duration period) {
        this.host = host;
        this.plcType = plcType;
        this.rack = rack;
        this.slot = slot;
        this.port = port;
        this.period = period;
    }

    public String getHost() {
        return host;
    }

    public SiemensPLCS getPlcType() {
        return plcType;
    }

    public int getRack() {
        return rack;
    }

    public int getSlot() {
        return slot;
    }

    public int getPort() {
        return port;
    }

    public Duration getPeriod() {
        return period;
    }
}
