package com.github.s7connector.impl;

import com.github.s7connector.api.S7Connector;
import com.github.s7connector.api.SiemensPLCS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class S7TCPConnectionHolder extends S7BaseConnectionHolder {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final AtomicLong THREAD_COUNTER = new AtomicLong();

    private final String host;

    private final SiemensPLCS plcType;

    private final int rack, slot, port;

    private volatile Duration timeout = Duration.ofSeconds(2);

    private final ScheduledExecutorService executor;

    public S7TCPConnectionHolder(String host,
                                 SiemensPLCS plcType,
                                 int rack,
                                 int slot,
                                 int port) {
        this.host = host;
        this.plcType = plcType;
        this.rack = rack;
        this.slot = slot;
        this.port = port;
        this.executor = Executors.newSingleThreadScheduledExecutor();
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

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    protected S7Connector doStartConnection() throws IOException {
        return new S7TCPConnection(host, rack, slot, port, (int) timeout.toMillis(), plcType);
    }

    @Override
    protected ScheduledExecutorService getExecutor() {
        return executor;
    }
}
