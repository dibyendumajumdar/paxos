package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.Decree;

import java.nio.ByteBuffer;

public class SuccessMessage implements PaxosMessage {

    final Decree decree;

    public SuccessMessage(Decree decree) {
        this.decree = decree;
    }

    public SuccessMessage(ByteBuffer bb) {
        this.decree = new Decree(bb);
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Decree.size());
        decree.store(bb);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.SUCCESS_MESSAGE;
    }
}
