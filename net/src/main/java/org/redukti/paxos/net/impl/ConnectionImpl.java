/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.net.impl;

import org.redukti.paxos.net.api.Connection;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.ResponseHandler;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionImpl extends ProtocolHandler implements Connection {

    final int id;
    final AtomicInteger requestId = new AtomicInteger(0);

    public ConnectionImpl(int id, EventLoopImpl eventLoop, SocketChannel socketChannel) {
        super(eventLoop);
        this.id = id;
        this.socketChannel = socketChannel;
    }

    @Override
    public void submit(ByteBuffer requestData, ResponseHandler responseHandler, Duration timeout) {
        MessageHeader header = new MessageHeader(true);
        MessageImpl request = new MessageImpl(header, requestData);
        request.setCorrelationId(new CorrelationId(id, requestId.incrementAndGet()));
        if (responseHandler != null) {
            eventLoop.queueResponseHandler(request, responseHandler);
        }
        queueWrite(new WriteRequest(request.getHeader(), request.getData()));
    }

    public void setErrored() {
        failed();
    }

    @Override
    public boolean isConnected() {
        return socketChannel != null && socketChannel.isConnected();
    }

    @Override
    public String toString() {
        return "Connection={" +
                "id=" + id +
                '}';
    }
}
