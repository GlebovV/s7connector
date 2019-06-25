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

public class S7TCPAsyncConnection extends S7BaseAsyncConnection {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final AtomicLong THREAD_COUNTER = new AtomicLong();

    private volatile String host;

    private volatile SiemensPLCS plcType;

    private volatile int rack, slot, port;

    private volatile Duration timeout = Duration.ofSeconds(2);

    private final ScheduledExecutorService executor;

    public S7TCPAsyncConnection(String host,
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

    //To take effect, connection must be restarted
    public void setHost(String host) {
        this.host = host;
    }

    //To take effect, connection must be restarted
    public void setPlcType(SiemensPLCS plcType) {
        this.plcType = plcType;
    }

    //To take effect, connection must be restarted
    public void setRack(int rack) {
        this.rack = rack;
    }

    //To take effect, connection must be restarted
    public void setSlot(int slot) {
        this.slot = slot;
    }

    //To take effect, connection must be restarted
    public void setPort(int port) {
        this.port = port;
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
