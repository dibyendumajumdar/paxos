package org.redukti.paxos.basic;

import org.redukti.paxos.net.impl.CorrelationId;

import java.nio.ByteBuffer;

public class ClientRequestMessage implements PaxosMessage {

    final CorrelationId correlationId;
    final long requestedValue;

    public ClientRequestMessage(CorrelationId correlationId, long requestedValue) {
        this.correlationId = correlationId;
        this.requestedValue = requestedValue;
    }

    public ClientRequestMessage(CorrelationId correlationId, ByteBuffer bb) {
        this.correlationId = correlationId;
        this.requestedValue = bb.getLong();
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+Long.BYTES);
        bb.putShort((short)getCode());
        bb.putLong(requestedValue);
        return bb.flip();
    }

    @Override
    public int getCode() {
        return PaxosMessages.CLIENT_REQUEST_MESSAGE;
    }

    @Override
    public String toString() {
        return "ClientRequestMessage{" +
                "correlationId=" + correlationId +
                ", requestedValue=" + requestedValue +
                '}';
    }
}
