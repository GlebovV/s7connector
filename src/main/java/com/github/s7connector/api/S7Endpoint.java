package com.github.s7connector.api;

import com.github.s7connector.exception.S7Exception;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface S7Endpoint extends Closeable {
    void start();

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

    Future<Void> write(ItemKey key, byte[] value);

    default Future<Void> write(DaveArea area,
                               int areaNumber,
                               int offset,
                               byte[] value) {
        return write(new ItemKey(area, areaNumber, value.length, offset), value);
    }
}
