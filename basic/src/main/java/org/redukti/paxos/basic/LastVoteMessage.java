package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;

import java.nio.ByteBuffer;

public class LastVoteMessage implements PaxosMessage {

    int p;
    BallotNum vBal;
    Decree vDec;

    public LastVoteMessage(int p, BallotNum vBal, Decree vDec) {
        this.p = p;
        this.vBal = vBal;
        this.vDec = vDec;
    }

    public LastVoteMessage(ByteBuffer bb) {
        this.p = bb.get();
        this.vBal = new BallotNum(bb);
        this.vDec = new Decree(bb);
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+Byte.BYTES+BallotNum.size()+Decree.size());
        bb.putShort((short)PaxosMessages.LAST_VOTE_MESSAGE);
        bb.put((byte) p);
        vBal.store(bb);
        vDec.store(bb);
        return bb.flip();
    }

    @Override
    public int getCode() {
        return PaxosMessages.LAST_VOTE_MESSAGE;
    }
}
