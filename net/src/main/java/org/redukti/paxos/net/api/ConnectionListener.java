package org.redukti.paxos.net.api;

public interface ConnectionListener {
    void onConnectionFailed();
    void onConnectionSuccess();
}
