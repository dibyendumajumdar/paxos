package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;

import java.nio.ByteBuffer;

public class BeginBallotMessage implements PaxosMessage {

    final BallotNum b;
    final Decree decree;

    public BeginBallotMessage(BallotNum b, Decree decree) {
        this.b = b;
        this.decree = decree;
    }

    public BeginBallotMessage(ByteBuffer bb) {
        b = new BallotNum(bb);
        decree = new Decree(bb);
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(BallotNum.size()+Decree.size());
        b.store(bb);
        decree.store(bb);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.BEGIN_BALLOT_MESSAGE;
    }

    @Override
    public String toString() {
        return "BeginBallotMessage{" +
                "b=" + b +
                ", decree=" + decree +
                '}';
    }
}
