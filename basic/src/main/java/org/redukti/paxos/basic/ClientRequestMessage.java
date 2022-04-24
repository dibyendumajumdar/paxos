package org.redukti.paxos.basic;

import java.nio.ByteBuffer;

public class ClientRequestMessage implements PaxosMessage {

    final long requestedValue;

    public ClientRequestMessage(long requestedValue) {
        this.requestedValue = requestedValue;
    }

    public ClientRequestMessage(ByteBuffer bb) {
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
}
