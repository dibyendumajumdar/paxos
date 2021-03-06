/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.net.api;

import java.nio.ByteBuffer;
import java.time.Duration;

public interface Connection {
    void submit(ByteBuffer requestData, ResponseHandler responseHandler, Duration timeout);
    boolean isConnected();

    boolean isErrored();
}
