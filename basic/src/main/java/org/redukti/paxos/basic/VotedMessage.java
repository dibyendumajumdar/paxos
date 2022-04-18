package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

public class VotedMessage implements PaxosMessage {

    final BallotNum prevBal;
    final int owner;

    public VotedMessage(ByteBuffer bb) {
        this.prevBal = new BallotNum(bb);
        this.owner = bb.get();
    }

    public VotedMessage(BallotNum prevBal, int id) {
        this.prevBal = prevBal;
        this.owner = id;
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(BallotNum.size()+Byte.BYTES);
        prevBal.store(bb);
        bb.put((byte) owner);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.VOTED_MESSAGE;
    }
}
