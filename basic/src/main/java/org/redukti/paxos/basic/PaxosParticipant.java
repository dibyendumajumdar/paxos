package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;

public abstract class PaxosParticipant {

    public abstract int getId();

    // equivalent to phase 1 (a) prepare request
    public abstract void sendNextBallot(BallotNum b);

    // equivalent to phase 1 (b) promise message
    public abstract void sendLastVoteMessage(BallotNum b, Vote v);

    // equivalent to phase 2 (a) accept request
    public abstract void sendBeginBallot(BallotNum b, Decree decree);

    // equivalent to phase 2 (b) accepted message
    public abstract void sendVoted(BallotNum prevBal, int id);

    public abstract void sendSuccess(Decree decree);
}
