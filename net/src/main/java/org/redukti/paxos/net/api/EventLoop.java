package org.redukti.paxos.net.api;

import java.time.Duration;

public interface EventLoop {

    Connection clientConnection(String address, int port, Duration timeout);

    void start(String serverAddress, int serverPort, RequestHandler requestHandler);

    void eventLoop();

    void requestStop();

    void shutdown();
}
