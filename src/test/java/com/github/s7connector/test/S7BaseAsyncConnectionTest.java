package com.github.s7connector.test;

import com.github.s7connector.api.DaveArea;
import com.github.s7connector.api.S7AsyncConnection;
import com.github.s7connector.api.S7Connector;
import com.github.s7connector.impl.S7BaseAsyncConnection;
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

public class S7BaseAsyncConnectionTest {

    @Test
    public void readTest() throws IOException {
        S7Connector connectorMock = Mockito.mock(S7Connector.class);
        ScheduledExecutorService executorServiceMock = Mockito.mock(ScheduledExecutorService.class);
        ArgumentCaptor<Runnable> rCaptor = ArgumentCaptor.forClass(Runnable.class);

        S7BaseAsyncConnection endpoint = new S7BaseAsyncConnection() {
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
        S7BaseAsyncConnection endpoint = new S7BaseAsyncConnection() {
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

    @Test
    public void startStopTest() throws IOException {
        S7Connector connectorMock = Mockito.mock(S7Connector.class);
        ScheduledExecutorService executorServiceMock = Mockito.mock(ScheduledExecutorService.class);
        ArgumentCaptor<Runnable> rCaptor = ArgumentCaptor.forClass(Runnable.class);
        final boolean[] startConnection = {false};
        S7BaseAsyncConnection endpoint = new S7BaseAsyncConnection() {
            @Override
            protected S7Connector doStartConnection() throws IOException {
                startConnection[0] = true;
                return connectorMock;
            }

            @Override
            protected ScheduledExecutorService getExecutor() {
                return executorServiceMock;
            }
        };
        Mockito.when(executorServiceMock.scheduleAtFixedRate(
                rCaptor.capture(),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.eq(TimeUnit.MILLISECONDS))).then(x -> {
            return Mockito.mock(ScheduledFuture.class);
        });

        Assert.assertEquals(S7AsyncConnection.State.Idle, endpoint.getState());
        AtomicReference<S7AsyncConnection.State> state = new AtomicReference<>(S7AsyncConnection.State.Idle);
        endpoint.setStateListener(state::set);

        //START
        endpoint.start();
        endpoint.start();
        endpoint.start();

        //CHECK IS STARTED
        Assert.assertFalse(startConnection[0]);
        rCaptor.getValue().run();
        Assert.assertTrue(startConnection[0]);
        Assert.assertEquals(S7AsyncConnection.State.Active, endpoint.getState());
        Mockito.verify(executorServiceMock, Mockito.times(1)).scheduleAtFixedRate(
                Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any());

        //STOP
        ArgumentCaptor<Runnable> stopCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.when(executorServiceMock.schedule(stopCaptor.capture(), Mockito.anyLong(), Mockito.any())).then(x ->
                Mockito.mock(ScheduledFuture.class)
        );
        endpoint.stop();

        //CHECK IT STOPPED
        stopCaptor.getValue().run();
        Assert.assertEquals(S7AsyncConnection.State.Idle, endpoint.getState());
        Mockito.verify(connectorMock, Mockito.times(1)).close();
    }

    @Test
    public void closeFromIdleTest() throws IOException {
        S7Connector connectorMock = Mockito.mock(S7Connector.class);
        ScheduledExecutorService executorServiceMock = Mockito.mock(ScheduledExecutorService.class);
        S7BaseAsyncConnection endpoint = new S7BaseAsyncConnection() {
            @Override
            protected S7Connector doStartConnection() throws IOException {
                return connectorMock;
            }

            @Override
            protected ScheduledExecutorService getExecutor() {
                return executorServiceMock;
            }
        };
        endpoint.close();
        Mockito.verify(executorServiceMock, Mockito.times(1)).shutdown();
    }

    @Test
    public void closeFromRunningTest() throws IOException {
        S7Connector connectorMock = Mockito.mock(S7Connector.class);
        ScheduledExecutorService executorServiceMock = Mockito.mock(ScheduledExecutorService.class);
        ArgumentCaptor<Runnable> rCaptor = ArgumentCaptor.forClass(Runnable.class);

        S7BaseAsyncConnection endpoint = new S7BaseAsyncConnection() {
            @Override
            protected S7Connector doStartConnection() throws IOException {
                return connectorMock;
            }

            @Override
            protected ScheduledExecutorService getExecutor() {
                return executorServiceMock;
            }
        };
        Mockito.when(executorServiceMock.scheduleAtFixedRate(
                rCaptor.capture(),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.eq(TimeUnit.MILLISECONDS))).then(x -> {
            return Mockito.mock(ScheduledFuture.class);
        });
        endpoint.start();
        rCaptor.getValue().run();
        Assert.assertEquals(S7AsyncConnection.State.Active, endpoint.getState());

        ArgumentCaptor<Runnable> stopCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.when(executorServiceMock.schedule(stopCaptor.capture(), Mockito.anyLong(), Mockito.any())).then(x ->
                Mockito.mock(ScheduledFuture.class)
        );
        endpoint.close();
        stopCaptor.getValue().run();
        Mockito.verify(connectorMock, Mockito.times(1)).close();
        Mockito.verify(executorServiceMock, Mockito.times(1)).shutdown();
    }
}