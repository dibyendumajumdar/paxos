package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

/**
 * Also known as message type "1b" or PROMISE message.
 */
public class LastVoteMessage implements PaxosMessage {

    static final String MESSAGE_TYPE = "PROMISE (1b)";

    /**
     * BallotNumber for which a promise is being made
     */
    BallotNum b;
    /**
     * The pair MaxVBal and MaxVal - represent the highest numbered
     * ballot / and its value that was previously voted on
     */
    Vote v;

    public LastVoteMessage(BallotNum b, Vote v) {
        this.b = b;
        this.v = v;
    }

    public LastVoteMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
        this.v = new Vote(bb);
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+BallotNum.size()+Vote.size());
        bb.putShort((short)getCode());
        b.store(bb);
        v.store(bb);
        return bb.flip();
    }

    @Override
    public int getCode() {
        return PaxosMessages.LAST_VOTE_MESSAGE;
    }

    @Override
    public String toString() {
        return "LastVoteMessage{" +
                "type=" + MESSAGE_TYPE +
                ", b=" + b +
                ", v=" + v +
                '}';
    }
}
