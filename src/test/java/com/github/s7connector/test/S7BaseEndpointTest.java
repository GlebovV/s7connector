package com.github.s7connector.test;

import com.github.s7connector.api.DaveArea;
import com.github.s7connector.api.S7Connector;
import com.github.s7connector.impl.S7BaseEndpoint;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class S7BaseEndpointTest {

    @Test
    public void readTest() throws IOException {
        S7Connector connectorMock = Mockito.mock(S7Connector.class);
        ScheduledExecutorService executorServiceMock = Mockito.mock(ScheduledExecutorService.class);
        ArgumentCaptor<Runnable> rCaptor = ArgumentCaptor.forClass(Runnable.class);

        S7BaseEndpoint endpoint = new S7BaseEndpoint() {
            @Override
            protected S7Connector doStartConnection() throws IOException {
                return connectorMock;
            }

            @Override
            protected ScheduledExecutorService getExecutor() {
                return executorServiceMock;
            }
        };
        Mockito.when(connectorMock.read(Mockito.any(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyInt())).thenReturn(new byte[]{0, 1, 0, 1});
        Mockito.when(executorServiceMock.scheduleAtFixedRate(
                rCaptor.capture(),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.eq(TimeUnit.MILLISECONDS))).then(x -> {
            return Mockito.mock(ScheduledFuture.class);
        });
        endpoint.start();
        AtomicReference<byte[]> readResult = new AtomicReference<>(null);
        endpoint.addItem(DaveArea.DB, 0, 0, 0, readResult::set);
        rCaptor.getValue().run();
        Assert.assertArrayEquals(new byte[]{0, 1, 0, 1}, readResult.get());
    }

    @Test
    public void writeTest() throws Exception {
        S7Connector connectorMock = Mockito.mock(S7Connector.class);
        ScheduledExecutorService executorServiceMock = Mockito.mock(ScheduledExecutorService.class);
        ArgumentCaptor<Callable<Void>> rCaptor = ArgumentCaptor.forClass(Callable.class);
        S7BaseEndpoint endpoint = new S7BaseEndpoint() {
            @Override
            protected S7Connector doStartConnection() throws IOException {
                return connectorMock;
            }

            @Override
            protected ScheduledExecutorService getExecutor() {
                return executorServiceMock;
            }
        };

        Mockito.when(executorServiceMock.schedule(
                rCaptor.capture(),
                Mockito.anyLong(),
                Mockito.any())).then(x -> Mockito.mock(ScheduledFuture.class, "b")

        );

        Mockito.when(executorServiceMock.scheduleAtFixedRate(
                Mockito.any(),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.eq(TimeUnit.MILLISECONDS))).then(x -> Mockito.mock(ScheduledFuture.class, "a"));
        endpoint.start();
        endpoint.write(DaveArea.DB, 0, 1, new byte[]{1, 0, 1, 0});
        rCaptor.getValue().call();
        Mockito.verify(connectorMock, Mockito.times(1))
                .write(Mockito.eq(DaveArea.DB), Mockito.eq(0), Mockito.eq(1), Mockito.eq(new byte[]{1, 0, 1, 0}));
    }
}