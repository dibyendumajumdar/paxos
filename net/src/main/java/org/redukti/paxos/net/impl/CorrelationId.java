package org.redukti.paxos.net.impl;

import java.util.Objects;

public class CorrelationId {
    public final int connectionId;
    public final long requestId;

    public CorrelationId(int connectionId, long requestId) {
        this.connectionId = connectionId;
        this.requestId = requestId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CorrelationId that = (CorrelationId) o;
        return connectionId == that.connectionId && requestId == that.requestId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, requestId);
    }

    @Override
    public String toString() {
        return "CorrelationId={" +
                "connectionId=" + connectionId +
                ", requestId=" + requestId +
                '}';
    }
}
