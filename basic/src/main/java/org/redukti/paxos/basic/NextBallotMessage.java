package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

public class NextBallotMessage implements PaxosMessage {
    final BallotNum b;

    public NextBallotMessage(BallotNum b) {
        this.b = b;
    }

    public NextBallotMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES + BallotNum.size());
        bb.putShort((short) PaxosMessages.NEXT_BALLOT_MESSAGE);
        b.store(bb);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.NEXT_BALLOT_MESSAGE;
    }

    @Override
    public String toString() {
        return "NextBallotMessage{" +
                "b=" + b +
                '}';
    }
}
