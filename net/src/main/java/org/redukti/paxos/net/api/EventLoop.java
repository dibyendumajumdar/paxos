package org.redukti.paxos.net.api;

import java.io.Closeable;
import java.time.Duration;

public interface EventLoop extends Closeable {

    Connection clientConnection(String address, int port, Duration timeout);

    void startServerChannel(String serverAddress, int serverPort, RequestHandler requestHandler);

    void select();
}
