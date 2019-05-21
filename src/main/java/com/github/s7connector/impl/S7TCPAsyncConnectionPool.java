package com.github.s7connector.impl;

import com.github.s7connector.api.S7AsyncConnection;
import com.github.s7connector.api.SiemensPLCS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class S7TCPAsyncConnectionPool {
    public final class Key {
        private final String host;
        private final SiemensPLCS plcType;
        private final int rack;
        private final int slot;
        private final int port;

        public Key(String host, SiemensPLCS plcType, int rack, int slot, int port) {
            this.host = host;
            this.plcType = plcType;
            this.rack = rack;
            this.slot = slot;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key that = (Key) o;
            return rack == that.rack &&
                    slot == that.slot &&
                    port == that.port &&
                    host.equals(that.host) &&
                    plcType == that.plcType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, plcType, rack, slot, port);
        }
    }

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private Map<Key, S7TCPAsyncConnection> endpoints = new HashMap<>();
    private Map<Key, AtomicInteger> acquires = new HashMap<>();

    synchronized public S7AsyncConnection acquireEndpoint(Key key) {
        if (endpoints.containsKey(key)) {
            S7TCPAsyncConnection endpoint = endpoints.get(key);
            acquires.get(key).incrementAndGet();
            return endpoint;

        } else {
            S7TCPAsyncConnection endpoint = new S7TCPAsyncConnection(key.getHost(), key.getPlcType(), key.getRack(), key.getSlot(), key.getPort());
            endpoints.put(key, endpoint);
            return endpoint;
        }
    }

    synchronized public void releaseEndpoint(Key key) {
        if (endpoints.containsKey(key)) {
            if (acquires.get(key).decrementAndGet() == 0) {
                try {
                    endpoints.get(key).close();
                } catch (IOException e) {
                    log.error("Error durning closing endpoint", e);
                }
                endpoints.remove(key);
                acquires.remove(key);
            }
        }
    }
}
