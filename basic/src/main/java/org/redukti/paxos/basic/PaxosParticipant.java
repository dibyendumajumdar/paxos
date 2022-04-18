package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;

public abstract class PaxosParticipant {

    public abstract void sendNextBallot(BallotNum b);

    protected abstract void sendLastVoteMessage(LastVotePaxosMessage lvp);

    public abstract int getId();
}
