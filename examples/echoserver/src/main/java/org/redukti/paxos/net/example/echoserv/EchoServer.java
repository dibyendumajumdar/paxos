package org.redukti.paxos.net.example.echoserv;

import org.redukti.logging.Logger;
import org.redukti.logging.LoggerFactory;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.redukti.paxos.net.api.RequestResponseSender;
import org.redukti.paxos.net.impl.EventLoopImpl;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class EchoServer implements RequestHandler {

    static final Logger log = LoggerFactory.DEFAULT.getLogger(EventLoopImpl.class.getName());

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
    public void handleRequest(Message request, RequestResponseSender responseSender) {
        log.info(getClass(), "handleRequest", "Handling request " + request.getCorrelationId());
        int value = request.getData().rewind().getInt();
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(value);
        bb.flip();
        responseSender.setData(bb);
        responseSender.submit();
        requests.incrementAndGet();
    }


}
