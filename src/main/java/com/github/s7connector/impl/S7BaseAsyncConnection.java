package com.github.s7connector.impl;

import com.github.s7connector.api.ItemKey;
import com.github.s7connector.api.S7AsyncConnection;
import com.github.s7connector.api.S7Connector;
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

public abstract class S7BaseAsyncConnection implements S7AsyncConnection {
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

    private volatile ScheduledFuture<?> pollJob = null;

    private volatile ScheduledFuture<?> deactivateJob = null;

    private volatile State state = State.Idle;

    private volatile Consumer<State> stateConsumer = null;

    private volatile Boolean closeFlag = false;

    private synchronized void setState(State state) {
        this.state = state;
        if (stateConsumer != null)
            stateConsumer.accept(state);
    }

    public State getState() {
        return state;
    }


    public void setStateListener(Consumer<State> listener) {
        stateConsumer = listener;
    }

    public Consumer<State> getStateListener() {
        return stateConsumer;
    }

    public Duration getPeriod() {
        return period;
    }

    public synchronized void setPeriod(Duration period) {
        if (this.period != period) {
            this.period = period;
            if (pollJob != null) {
                pollJob.cancel(false);
                pollJob = getExecutor().scheduleAtFixedRate(this::poll, 0, period.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public synchronized void start() {
        if (state == State.Idle) {
            logger.debug("Start connection");
            ScheduledExecutorService executorService = getExecutor();
            setState(State.Active);
            if (deactivateJob != null) {
                deactivateJob.cancel(false);
                deactivateJob = null;
            }
            pollJob = executorService.scheduleAtFixedRate(this::poll, 0, period.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        if (state == State.Active && deactivateJob == null) {
            logger.debug("Stop connection");
            ScheduledExecutorService executorService = getExecutor();
            if (pollJob != null) {
                pollJob.cancel(false);
                deactivateJob = getExecutor().schedule(() -> {
                    try {
                        closeConnection();
                        logger.debug("Connection successfully closed");
                    } catch (Exception e) {
                        logger.error("Error while closing connection", e);
                    } finally {
                        synchronized (this) {
                            if (deactivateJob != null) {
                                pollJob = null;
                                deactivateJob = null;
                            }
                            if (closeFlag) {
                                getExecutor().shutdown();
                                setState(State.Closed);
                            } else
                                setState(State.Idle);
                        }
                    }
                }, 0, TimeUnit.MILLISECONDS);
            } else
                setState(State.Idle);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (state != State.Closed) {
            logger.debug("Close connection");
            ScheduledExecutorService scheduledExecutorService = getExecutor();
            if (state == State.Active)
                stop();
            if (deactivateJob == null)
                getExecutor().shutdown();
            else
                closeFlag = true;
            setState(State.Closed);
        }
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
    public synchronized CompletableFuture<Void> write(ItemKey key, byte[] value) {
        AtomicReference<Future<Void>> fRef = new AtomicReference<>();
        CompletableFuture<Void> cf = new CompletableFuture<Void>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (super.cancel(mayInterruptIfRunning)) {
                    Future<Void> f = fRef.get();
                    if (f != null)
                        return f.cancel(mayInterruptIfRunning);
                    else
                        return false;
                } else return false;
            }

            public ScheduledFuture<Void> sf;
        };
        logger.debug("Write {} -> {}", key, value);
        if (state != State.Active)
            throw new IllegalStateException("Connection not active");
        fRef.set(getExecutor().schedule(() -> {
            try {
                doWrite(key, value);
                cf.complete(null);
            } catch (Throwable e) {
                cf.completeExceptionally(e);
            }
            return null;
        }, 0, TimeUnit.MILLISECONDS));
        return cf;
    }

    protected abstract S7Connector doStartConnection() throws IOException;

    protected abstract ScheduledExecutorService getExecutor();

    private void startConnection() {
        synchronized (connectionLock) {
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

    private void checkConnectionAndDo(Runnable c) throws IOException {
        synchronized (connectionLock) {
            if (connection != null) {
                c.run();
            } else {
                startConnection();
                if (connection != null)
                    c.run();
                else
                    throw new IOException("Connection is broken");
            }
        }
    }

    private void poll() {
        try {
            checkConnectionAndDo(() -> {
                try {
                    doRead();
                } catch (Exception e) {
                    logger.error("Error during read cycle", e);
                }
            });
        } catch (IOException e) {
            logger.warn("Error while polling, retry next poll", e);
            synchronized (this) {
                try {
                    if (exceptionConsumer != null)
                        exceptionConsumer.accept(e);
                } catch (Exception ne) {
                    logger.error("Error while call exception consumer", ne);
                }
            }
        }
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
                Consumer<S7Exception> ec = proc.exceptionConsumer;
                try {
                    if (ec != null)
                        proc.exceptionConsumer.accept(e);
                } catch (Exception ne) {
                    logger.error("Error while call exception consumer", ne);
                }
            } catch (IOException e) {
                logger.error("Global read error", e);
                error.set(true);
                Consumer<IOException> ec = exceptionConsumer;
                try {
                    if (ec != null)
                        exceptionConsumer.accept(e);
                } catch (Exception ne) {
                    logger.error("Error while call exception consumer", ne);
                }
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
