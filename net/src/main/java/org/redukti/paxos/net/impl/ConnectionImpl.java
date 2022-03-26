package org.redukti.paxos.net.impl;

import org.redukti.paxos.net.api.Connection;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.ResponseHandler;

import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionImpl extends ProtocolHandler implements Connection {

    int id;
    AtomicInteger requestId = new AtomicInteger(0);

    public ConnectionImpl(int id, EventLoopImpl eventLoop, SocketChannel socketChannel) {
        super(eventLoop);
        this.id = id;
        this.socketChannel = socketChannel;
    }

    @Override
    public void submit(Message request, ResponseHandler responseHandler, Duration timeout) {
        request.setCorrelationId(new CorrelationId(id, requestId.incrementAndGet()));
        if (responseHandler != null) {
            networkServer.queueResponseHandler(request, responseHandler);
        }
        this.writeQueue.add(new WriteRequest(request.getHeader(), request.getData()));
    }
}
