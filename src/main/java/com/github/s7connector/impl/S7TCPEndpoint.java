package com.github.s7connector.impl;

import com.github.s7connector.api.EndpointKey;
import com.github.s7connector.api.ItemKey;
import com.github.s7connector.api.S7Endpoint;
import com.github.s7connector.api.SiemensPLCS;
import com.github.s7connector.exception.S7Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class S7TCPEndpoint implements S7Endpoint {
    private static class ItemProcessor {
        final Consumer<byte[]> consumer;

        final Consumer<S7Exception> exceptionConsumer;

        private ItemProcessor(Consumer<byte[]> consumer, Consumer<S7Exception> exceptionConsumer) {
            this.consumer = consumer;
            this.exceptionConsumer = exceptionConsumer;
        }

        public Consumer<byte[]> getConsumer() {
            return consumer;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final AtomicLong THREAD_COUNTER = new AtomicLong();

    private final String host;

    private final SiemensPLCS plcType;

    private final int rack, slot, port;

    private final Duration timeout;

    private final ScheduledExecutorService executor;

    private final Map<ItemKey, ItemProcessor> items = new ConcurrentHashMap<>();

    private final Map<Duration, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    private volatile Consumer<IOException> exceptionConsumer = null;

    private volatile S7TCPConnection connection;

    public S7TCPEndpoint(EndpointKey key) {
        this(key.getHost(), key.getPlcType(), key.getRack(), key.getSlot(), key.getPort(), Duration.ofSeconds(2), key.getPeriod());
    }

    public S7TCPEndpoint(String host,
                         SiemensPLCS plcType,
                         int rack,
                         int slot,
                         int port,
                         Duration timeout,
                         Duration period) {
        this.host = host;
        this.plcType = plcType;
        this.rack = rack;
        this.slot = slot;
        this.port = port;
        this.timeout = timeout;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread("S7Exec/" + THREAD_COUNTER.getAndIncrement()));
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


    @Override
    public void addItem(ItemKey key, Consumer<byte[]> consumer) {
        items.put(key, new ItemProcessor(consumer, null));
    }

    @Override
    public void addItem(ItemKey key, Consumer<byte[]> consumer, Consumer<S7Exception> exceptionConsumer) {
        items.put(key, new ItemProcessor(consumer, exceptionConsumer));
    }

    @Override
    public void removeItem(ItemKey key) {
        items.remove(key);
    }

    @Override
    public void setExceptionConsumer(Consumer<IOException> consumer) {
        this.exceptionConsumer = consumer;
    }

    @Override
    public void removeExceptionConsumer() {
        this.exceptionConsumer = null;
    }

    @Override
    public Future<Void> write(ItemKey key, byte[] value) {
        return executor.schedule(() -> {
            doWrite(key, value);
            return null;
        }, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
    }

    synchronized private void startConnection() throws IOException {
        if (connection == null)
            connection = new S7TCPConnection(host, rack, slot, port, (int) timeout.toMillis(), plcType);
    }

    synchronized private void closeConnection() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private void checkConnectionAndDo(Runnable c) {
        if (connection != null) {
            c.run();
        } else {
            try {
                startConnection();
            } catch (IOException e) {
                logger.error("Error during connection establishment", e);
            }
        }
    }

    private void poll() {
        checkConnectionAndDo(() -> {
            try {
                doRead();
            } catch (Exception e) {
                logger.error("Error during read cycle", e);
            }
        });
    }

    synchronized private void doRead() throws IOException {
        AtomicBoolean error = new AtomicBoolean(false);
        items.forEach((key, proc) -> {
            try {
                if (!error.get()) {
                    byte[] result = connection.read(key.getArea(), key.getAreaNumber(), key.getBytes(), key.getOffset());
                    proc.consumer.accept(result);
                }
            } catch (S7Exception e) {
                logger.warn("Item read error", e);
                proc.exceptionConsumer.accept(e);
            } catch (IOException e) {
                logger.error("Global read error", e);
                error.set(true);
                exceptionConsumer.accept(e);
                closeConnection();
            }
        });
    }

    synchronized private void doWrite(ItemKey key, byte[] value) throws Exception {
        AtomicReference<Exception> ex = null;
        checkConnectionAndDo(() -> {
                    try {
                        connection.write(key.getArea(), key.getAreaNumber(), key.getOffset(), value);
                    } catch (IOException e) {
                        ex.set(e);
                    }
                }
        );
        if (ex.get() != null)
            throw ex.get();
    }
}
