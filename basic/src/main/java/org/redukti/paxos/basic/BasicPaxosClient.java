package org.redukti.paxos.basic;

import org.redukti.paxos.net.api.Connection;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.ResponseHandler;
import org.redukti.paxos.net.impl.EventLoopImpl;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.redukti.paxos.basic.PaxosMessages.CLIENT_REQUEST_MESSAGE;

public class BasicPaxosClient implements ResponseHandler {
    static AtomicInteger received = new AtomicInteger(0);

    public static void main(String[] args) {

        BasicPaxosClient m = new BasicPaxosClient();
        try (EventLoopImpl eventLoop = new EventLoopImpl()) {

            //eventLoop.start("localhost", 9001, m);
            Connection connection = eventLoop.clientConnection("localhost", 9000, null);

            eventLoop.select();
            eventLoop.select();

            if (connection.isConnected()) {
                System.out.println("Sending request");
                connection.submit(makeRequest(), m, Duration.ofSeconds(1));
            }

            while (received.get() < 1)
                eventLoop.select();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static ByteBuffer makeRequest() {
        ByteBuffer bb = ByteBuffer.allocate(Long.BYTES + Short.BYTES);
        bb.putShort((short) CLIENT_REQUEST_MESSAGE);
        bb.putLong(521);
        bb.flip();
        return bb;
    }

    @Override
    public void onTimeout() {

    }

    @Override
    public void onException(Exception e) {

    }

    @Override
    public void onResponse(Message response) {
        long value = response.getData().rewind().getLong();
        System.out.println("Received back " + value);
        received.incrementAndGet();
    }
}
