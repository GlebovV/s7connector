package com.github.s7connector.impl;

import com.github.s7connector.api.EndpointKey;
import com.github.s7connector.api.S7EndpointPool;
import com.github.s7connector.api.S7Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class S7TCPEndpointPool implements S7EndpointPool {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    private Map<EndpointKey, S7TCPEndpoint> endpoints = new HashMap<>();
    private Map<EndpointKey, AtomicInteger> acquires = new HashMap<>();

    @Override
    synchronized public S7Endpoint acquireEndpoint(EndpointKey key) {
        if (endpoints.containsKey(key)) {
            S7TCPEndpoint endpoint = endpoints.get(key);
            acquires.get(key).incrementAndGet();
            return endpoint;

        } else {
            S7TCPEndpoint endpoint = new S7TCPEndpoint(key);
            endpoints.put(key, endpoint);
            return endpoint;
        }
    }

    @Override
    synchronized public void releaseEndpoint(EndpointKey key) {
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
