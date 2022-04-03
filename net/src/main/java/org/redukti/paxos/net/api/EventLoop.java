/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.net.api;

import java.io.Closeable;

public interface EventLoop extends Closeable {

    Connection clientConnection(String address, int port, ConnectionListener connectionListener);

    void startServerChannel(String serverAddress, int serverPort, RequestHandler requestHandler);

    void select();
}
