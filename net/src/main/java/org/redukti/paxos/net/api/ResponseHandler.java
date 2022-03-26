package org.redukti.paxos.net.api;

public interface ResponseHandler {
    void onTimeout();
    void onException(Exception e);
    void onResponse(Message response);
}
