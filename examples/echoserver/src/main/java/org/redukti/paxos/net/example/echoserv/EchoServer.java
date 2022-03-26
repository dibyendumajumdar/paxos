package org.redukti.paxos.net.example.echoserv;

import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.redukti.paxos.net.api.ResponseHandler;
import org.redukti.paxos.net.impl.EventLoopImpl;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class EchoServer implements RequestHandler, ResponseHandler {

    static AtomicInteger requests = new AtomicInteger(0);

    public static void main(String[] args) {

        EchoServer m = new EchoServer();
        try (EventLoopImpl eventLoop = new EventLoopImpl()) {

            //eventLoop.start("localhost", 9001, m);
            eventLoop.start("localhost", 9001, m);

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
        System.err.println("Handling request");
        int value = request.getData().rewind().getInt();
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(value);
        bb.flip();
        response.setData(bb);
        requests.incrementAndGet();
    }

    @Override
    public void onTimeout() {

    }

    @Override
    public void onException(Exception e) {

    }

    @Override
    public void onResponse(Message response) {
        throw new UnsupportedOperationException();
    }

}
