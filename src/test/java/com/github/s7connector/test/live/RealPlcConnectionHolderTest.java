package com.github.s7connector.test.live;

import com.github.s7connector.api.SiemensPLCS;
import com.github.s7connector.impl.S7TCPConnectionHolder;
import org.junit.Test;

public class RealPlcConnectionHolderTest {
    @Test
    public void connectionTest() throws InterruptedException {
        S7TCPConnectionHolder ch = new S7TCPConnectionHolder("192.168.0.100", SiemensPLCS.SNon200, 0, 2, 102);
        ch.start();
        Thread.sleep(100000);
    }
}
