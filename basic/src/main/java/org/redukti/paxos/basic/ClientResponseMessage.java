package org.redukti.paxos.basic;

import java.nio.ByteBuffer;

public class ClientResponseMessage implements PaxosMessage {

    final long agreedValue;

    public ClientResponseMessage(long agreedValue) {
        this.agreedValue = agreedValue;
    }

    public ClientResponseMessage(ByteBuffer bb) {
        this.agreedValue = bb.getLong();
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+Long.BYTES);
        bb.putShort((short)getCode());
        bb.putLong(agreedValue);
        return bb.flip();
    }

    @Override
    public int getCode() {
        return PaxosMessages.CLIENT_RESPONSE_MESSAGE;
    }

    @Override
    public String toString() {
        return "ClientResponseMessage{" +
                "agreedValue=" + agreedValue +
                '}';
    }
}
