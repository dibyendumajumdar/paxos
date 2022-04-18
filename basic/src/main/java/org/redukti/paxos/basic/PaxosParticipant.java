package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;

public abstract class PaxosParticipant {

    public abstract int getId();

    public abstract void sendNextBallot(BallotNum b);

    public abstract void sendLastVoteMessage(LastVoteMessage lvp);

    public abstract void sendBeginBallot(BallotNum b, Decree decree);

    public abstract void sendVoted(BallotNum prevBal, int id);

    public abstract void sendSuccess(Decree decree);
}
