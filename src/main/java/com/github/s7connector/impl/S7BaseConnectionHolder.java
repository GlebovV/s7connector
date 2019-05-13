package com.github.s7connector.impl;

import com.github.s7connector.api.ItemKey;
import com.github.s7connector.api.S7Connector;
import com.github.s7connector.api.S7ConnectionHolder;
import com.github.s7connector.exception.S7Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class S7BaseConnectionHolder implements S7ConnectionHolder {
    private static class ItemProcessor {
        final Consumer<byte[]> consumer;

        final Consumer<S7Exception> exceptionConsumer;

        private ItemProcessor(Consumer<byte[]> consumer, Consumer<S7Exception> exceptionConsumer) {
            this.consumer = consumer;
            this.exceptionConsumer = exceptionConsumer;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private volatile Duration period = Duration.ofSeconds(1);

    private final Map<ItemKey, ItemProcessor> items = new ConcurrentHashMap<>();

    private volatile Consumer<IOException> exceptionConsumer = null;

    private volatile S7Connector connection;

    private final Object connectionLock = new Object();

    private volatile ScheduledFuture<?> job = null;

    public Duration getPeriod() {
        return period;
    }

    public synchronized void setPeriod(Duration period) {
        if (this.period != period) {
            this.period = period;
            if (job != null) {
                job.cancel(false);
                job = getExecutor().scheduleAtFixedRate(this::poll, 0, period.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public synchronized void start() {
        logger.debug("Start endpoint");
        ScheduledExecutorService executorService = getExecutor();
        if (executorService.isShutdown())
            throw new IllegalStateException("Endpoint is closed");
        if (job == null)
            job = executorService.scheduleAtFixedRate(this::poll, 0, period.toMillis(), TimeUnit.MILLISECONDS);
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
    public synchronized Future<Void> write(ItemKey key, byte[] value) {
        logger.debug("Write {} -> {}", key, value);
        if (job == null)
            throw new IllegalStateException("Endpoint not started");
        return getExecutor().schedule(() -> {
            doWrite(key, value);
            return null;
        }, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() throws IOException {
        logger.debug("Close endpoint");
        ScheduledExecutorService scheduledExecutorService = getExecutor();
        if (!getExecutor().isShutdown()) {
            getExecutor().shutdown();
            job = null;
        }
    }

    protected abstract S7Connector doStartConnection() throws IOException;

    protected abstract ScheduledExecutorService getExecutor();

    private void startConnection() {
        synchronized (connectionLock) {
            logger.debug("Starting connection");
            if (connection == null) {
                try {
                    connection = doStartConnection();
                } catch (IOException e) {
                    logger.error("Error during connection establishment", e);
                }
            }
        }
    }

    private void closeConnection() {
        synchronized (connectionLock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    logger.error("Error while closing connection", e);
                }
                connection = null;
            }
        }
    }

    private void checkConnectionAndDo(Runnable c) {
        synchronized (connectionLock) {
            if (connection != null) {
                c.run();
            } else {
                startConnection();
                if (connection != null)
                    c.run();
                else
                    throw new RuntimeException("Connection is broken");
            }
        }
    }

    private void poll() {
        logger.trace("Polling");
        checkConnectionAndDo(() -> {
            try {
                doRead();
            } catch (Exception e) {
                logger.error("Error during read cycle", e);
            }
        });
    }

    private void doRead() throws IOException {
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

    private void doWrite(ItemKey key, byte[] value) throws Exception {
        AtomicReference<Exception> ex = new AtomicReference<>(null);
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
