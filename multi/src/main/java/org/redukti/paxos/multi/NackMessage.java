package org.redukti.paxos.multi;

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

/**
 * NackMessage is sent by an acceptor when they are ahead of the proposer
 */
public class NackMessage implements PaxosMessage {

    /**
     * BallotNumber of new ballot being started
     */
    final BallotNum b;

    final BallotNum maxBal;

    /**
     * Sender id;
     */
    final int pid;

    public NackMessage(BallotNum b, BallotNum maxBal, int pid) {
        this.b = b;
        this.maxBal = maxBal;
        this.pid = pid;
    }

    public NackMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
        this.maxBal = new BallotNum(bb);
        this.pid = bb.getInt();
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES + 2*BallotNum.size() + Integer.BYTES);
        bb.putShort((short) getCode());
        b.store(bb);
        maxBal.store(bb);
        bb.putInt(pid);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.NACK_MESSAGE;
    }

    @Override
    public String toString() {
        return "NackMessage{" +
                "b=" + b +
                ", maxBal=" + maxBal +
                ", pid=" + pid +
                '}';
    }
}
