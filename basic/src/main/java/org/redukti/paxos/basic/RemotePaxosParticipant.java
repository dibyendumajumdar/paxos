package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.ResponseHandler;

import java.time.Duration;

public class RemotePaxosParticipant extends PaxosParticipant implements ResponseHandler {

    int id;
    ProcessChannel remote;

    public RemotePaxosParticipant(int id, ProcessChannel remote) {
        this.id = id;
        this.remote = remote;
    }

    @Override
    public void sendNextBallot(BallotNum b) {
        remote.connection.submit(new NextBallotPaxosMessage(b).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    protected void sendLastVoteMessage(LastVotePaxosMessage lvp) {
        remote.connection.submit(lvp.serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void sendBeginBallot(BallotNum b, Decree decree) {
        remote.connection.submit(new BeginBallotMessage(b, decree).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public void sendVoted(BallotNum prevBal, int id) {
        remote.connection.submit(new VotedMessage(prevBal, id).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public void sendSuccess(Decree decree) {
        remote.connection.submit(new SuccessMessage(decree).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public void onTimeout() {

    }

    @Override
    public void onException(Exception e) {

    }

    @Override
    public void onResponse(Message response) {

    }
}
