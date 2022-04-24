/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.net.api;

public interface RequestHandler {
    void handleRequest(Message request, RequestResponseSender responseSender);
}
