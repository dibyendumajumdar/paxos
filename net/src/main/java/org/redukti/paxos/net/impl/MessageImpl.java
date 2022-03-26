package org.redukti.paxos.net.impl;

import org.redukti.paxos.net.api.Message;

import java.nio.ByteBuffer;

public class MessageImpl implements Message {
    MessageHeader header;
    ByteBuffer data;

    public MessageImpl(MessageHeader header, ByteBuffer data) {
        this.header = header;
        header.setDataSize(data.limit());
        this.data = data;
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
        this.header.setDataSize(data.limit());
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

    @Override
    public void setCorrelationId(CorrelationId correlationId) {
        header.setCorrelationId(correlationId);
    }
}
