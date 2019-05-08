package com.github.s7connector.api;

public interface S7EndpointPool {
    S7Endpoint acquireEndpoint(EndpointKey key);

    void releaseEndpoint(EndpointKey key);
}
