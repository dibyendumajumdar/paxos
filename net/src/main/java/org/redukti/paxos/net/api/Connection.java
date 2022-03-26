package org.redukti.paxos.net.api;

import java.time.Duration;

public interface Connection {
    void submit(Message request, ResponseHandler responseHandler, Duration timeout);
}
