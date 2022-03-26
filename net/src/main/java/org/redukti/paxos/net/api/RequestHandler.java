package org.redukti.paxos.net.api;

public interface RequestHandler {
    void handleRequest(Message request, Message response);
}
