package org.redukti.paxos.net.example.echoclient;

import org.redukti.paxos.net.api.Connection;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.redukti.paxos.net.api.ResponseHandler;
import org.redukti.paxos.net.impl.EventLoopImpl;
import org.redukti.paxos.net.impl.MessageHeader;
import org.redukti.paxos.net.impl.MessageImpl;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class EchoClient implements RequestHandler, ResponseHandler {

    static AtomicInteger received = new AtomicInteger(0);

    public static void main(String[] args) {

        EchoClient m = new EchoClient();
        try (EventLoopImpl eventLoop = new EventLoopImpl()) {

            //eventLoop.start("localhost", 9001, m);
            Connection connection = eventLoop.clientConnection("localhost", 9001, Duration.ofSeconds(1));

            eventLoop.select();
            eventLoop.select();

            if (connection.isConnected()) {
                System.out.println("Sending value");
                connection.submit(makeRequest(), m, Duration.ofSeconds(1));
            }

            while (received.get() == 0)
                eventLoop.select();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static Message makeRequest() {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(42);
        bb.flip();
        MessageHeader header = new MessageHeader();
        return new MessageImpl(header, bb);
    }


    @Override
    public void handleRequest(Message request, Message response) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onTimeout() {

    }

    @Override
    public void onException(Exception e) {

    }

    @Override
    public void onResponse(Message response) {
        int value = response.getData().rewind().getInt();
        System.out.println("Received back " + value);
        received.incrementAndGet();
    }
}
