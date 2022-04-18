package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

public class NextBallotPaxosMessage implements PaxosMessage {
    final BallotNum b;

    public NextBallotPaxosMessage(BallotNum b) {
        this.b = b;
    }

    public NextBallotPaxosMessage(ByteBuffer bb) {
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

}
