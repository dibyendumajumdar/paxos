/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */

package org.redukti.paxos.net.impl;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MessageHeader {
    CorrelationId correlationId;
    int dataSize = 0;
    boolean hasException = false;
    boolean isRequest;

    public MessageHeader() {
        this.isRequest = true;
    }

    MessageHeader(boolean isRequest) {
        this.isRequest = isRequest;
    }

    public CorrelationId getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(CorrelationId correlationId) {
        this.correlationId = correlationId;
    }

    public int getDataSize() {
        return dataSize;
    }

    public void setDataSize(int dataSize) {
        this.dataSize = dataSize;
    }

    public boolean hasException() {
        return hasException;
    }

    public void setHasException(boolean hasException) {
        this.hasException = hasException;
    }

    static ByteBuffer allocate() {
        return ByteBuffer.allocate(22);
    }

    void store(ByteBuffer bb) {
        bb.put((byte) 'S');
        bb.put((byte) 'd');
        bb.put((byte) 'B');
        bb.put((byte) 'm');
        bb.putInt(correlationId.connectionId);
        bb.putLong(correlationId.requestId);
        bb.putInt(dataSize);
        bb.put((byte) (isRequest ? 1 : 0));
        bb.put((byte) (hasException ? 1 : 0));
    }

    void retrieve(ByteBuffer bb) throws IOException {
        byte c1 = bb.get();
        byte c2 = bb.get();
        byte c3 = bb.get();
        byte c4 = bb.get();
        if (c1 != 'S' || c2 != 'd' || c3 != 'B' || c4 != 'm') {
            throw new IOException("Invalid header");
        }
        int connId = bb.getInt();
        long reqId = bb.getLong();
        correlationId = new CorrelationId(connId, reqId);
        dataSize = bb.getInt();
        byte b = bb.get();
        isRequest = b == 1;
        b = bb.get();
        hasException = b == 1;
    }

    @Override
    public String toString() {
        return "MessageHeader={" +
                correlationId +
                ", dataSize=" + dataSize +
                ", isRequest=" + isRequest +
                '}';
    }
}
