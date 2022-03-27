/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.net.api;

public interface ResponseHandler {
    void onTimeout();
    void onException(Exception e);
    void onResponse(Message response);
}
