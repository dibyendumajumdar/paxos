package org.redukti.paxos.net.api;

import org.redukti.paxos.net.impl.CorrelationId;
import org.redukti.paxos.net.impl.MessageHeader;

import java.nio.ByteBuffer;

public interface Message {
    MessageHeader getHeader();
    CorrelationId getCorrelationId();
    ByteBuffer getData();
    void setCorrelationId(CorrelationId correlationId);
    void setData(ByteBuffer data);
}
