package com.github.s7connector.api;

import com.github.s7connector.exception.S7Exception;
import com.sun.istack.internal.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface S7AsyncConnection extends Closeable {
    enum State {
        Idle, Active, Closed
    }

    void start();

    void stop();

    State getState();

    void setStateListener(@Nullable Consumer<State> listener);

    @Nullable
    Consumer<State> getStateListener();

    default void addItem(DaveArea area,
                         int areaNumber,
                         int bytes,
                         int offset,
                         Consumer<byte[]> consumer) {
        addItem(new ItemKey(area, areaNumber, bytes, offset), consumer);
    }

    void addItem(ItemKey key, Consumer<byte[]> consumer);

    void addItem(ItemKey key, Consumer<byte[]> consumer, Consumer<S7Exception> exceptionConsumer);

    void removeItem(ItemKey key);

    default void removeItem(DaveArea area,
                            int areaNumber,
                            int bytes,
                            int offset) {
        removeItem(new ItemKey(area, areaNumber, bytes, offset));
    }

    void setExceptionConsumer(Consumer<IOException> consumer);

    void removeExceptionConsumer();

    CompletableFuture<Void> write(ItemKey key, byte[] value);

    default CompletableFuture<Void> write(DaveArea area,
                                          int areaNumber,
                                          int offset,
                                          byte[] value) {
        return write(new ItemKey(area, areaNumber, value.length, offset), value);
    }
}
