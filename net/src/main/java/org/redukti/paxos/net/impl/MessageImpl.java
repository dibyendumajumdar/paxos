package org.redukti.paxos.net.impl;

import org.redukti.paxos.net.api.Message;

import java.nio.ByteBuffer;

public class MessageImpl implements Message {
    MessageHeader header;
    ByteBuffer data;

    public MessageImpl(MessageHeader header, ByteBuffer data) {
        this.header = header;
        setDataSize(header, data);
        this.data = data;
    }

    private void setDataSize(MessageHeader header, ByteBuffer data) {
        int size = data.limit()- data.position();
        if (size < 0) {
            throw new IllegalArgumentException("Message data is invalid");
        }
        header.setDataSize(size);
    }

    @Override
    public CorrelationId getCorrelationId() {
        return header.getCorrelationId();
    }

    public ByteBuffer getData() {
        return data;
    }

    public int getDataSize() {
        return header.getDataSize();
    }

    public void setData(ByteBuffer data) {
        this.data = data;
        setDataSize(header, data);
    }

    public MessageHeader getHeader() {
        return header;
    }

    void setHeader(MessageHeader header) {
        this.header = header;
    }

    public ByteBuffer getHeaderData() {
        ByteBuffer headerData = MessageHeader.allocate();
        header.store(headerData);
        headerData.flip();
        return headerData;
    }

    public void setCorrelationId(CorrelationId correlationId) {
        header.setCorrelationId(correlationId);
    }

    @Override
    public String toString() {
        return "Message={" +
                header +
                ",data=..." +
                '}';
    }
}
