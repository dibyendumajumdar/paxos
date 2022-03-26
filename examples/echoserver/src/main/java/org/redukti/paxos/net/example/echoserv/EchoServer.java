package org.redukti.paxos.net.example.echoserv;

import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.redukti.paxos.net.impl.EventLoopImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class EchoServer implements RequestHandler {

    static final Logger log = LoggerFactory.getLogger(EventLoopImpl.class);

    static AtomicInteger requests = new AtomicInteger(0);

    public static void main(String[] args) {

        EchoServer m = new EchoServer();
        try (EventLoopImpl eventLoop = new EventLoopImpl()) {

            //eventLoop.start("localhost", 9001, m);
            eventLoop.startServerChannel("localhost", 9001, m);

            while (true) {
                eventLoop.select();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleRequest(Message request, Message response) {
        log.info("Handling request " + request.getCorrelationId());
        int value = request.getData().rewind().getInt();
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(value);
        bb.flip();
        response.setData(bb);
        requests.incrementAndGet();
    }


}
