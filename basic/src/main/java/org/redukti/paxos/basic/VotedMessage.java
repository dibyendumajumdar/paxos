package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

public class VotedMessage implements PaxosMessage {

    final BallotNum b;
    final int owner;

    public VotedMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
        this.owner = bb.get();
    }

    public VotedMessage(BallotNum b, int id) {
        this.b = b;
        this.owner = id;
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+BallotNum.size()+Byte.BYTES);
        bb.putShort((short)getCode());
        b.store(bb);
        bb.put((byte) owner);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.VOTED_MESSAGE;
    }

    @Override
    public String toString() {
        return "VotedMessage{" +
                "prevBal=" + b +
                ", owner=" + owner +
                '}';
    }
}
