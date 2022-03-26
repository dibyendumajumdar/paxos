package org.redukti.paxos.net.api;

public class NetException extends RuntimeException {
    public NetException(String message) {
        super(message);
    }

    public NetException(String message, Throwable cause) {
        super(message, cause);
    }
}
