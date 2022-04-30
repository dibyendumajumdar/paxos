package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

/**
 * Also known as PREPARE message or message type "1a"
 */
public class NextBallotMessage implements PaxosMessage {

    static final String MESSAGE_TYPE = "PREPARE (1a)";

    /**
     * BallotNumber of new ballot being started
     */
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
        bb.putShort((short) getCode());
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
                "type=" + MESSAGE_TYPE +
                ", b=" + b +
                '}';
    }
}
