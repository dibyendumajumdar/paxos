package org.redukti.paxos.net.api;

import org.redukti.paxos.net.impl.CorrelationId;

import java.nio.ByteBuffer;

public interface Message {
    CorrelationId getCorrelationId();
    ByteBuffer getData();
    void setData(ByteBuffer data);
}
