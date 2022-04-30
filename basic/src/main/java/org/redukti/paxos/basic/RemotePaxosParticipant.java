package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class RemotePaxosParticipant extends PaxosParticipant implements ResponseHandler {

    final static Logger log = LoggerFactory.getLogger(RemotePaxosParticipant.class);

    int id;
    ProcessChannel remote;

    public RemotePaxosParticipant(int id, ProcessChannel remote) {
        this.id = id;
        this.remote = remote;
    }

    @Override
    public int getId() {
        return id;
    }

    PaxosMessage logit(PaxosMessage m) {
        log.info("Sending " + m + " to " + remote);
        return m;
    }

    @Override
    public void sendNextBallot(BallotNum b) {
        remote.connection.submit(logit(new NextBallotMessage(b)).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public void sendLastVoteMessage(BallotNum b, Vote v) {
        remote.connection.submit(logit(new LastVoteMessage(b, v)).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public void sendBeginBallot(BallotNum b, Decree decree) {
        remote.connection.submit(logit(new BeginBallotMessage(b, decree)).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public void sendVoted(BallotNum prevBal, int id) {
        remote.connection.submit(logit(new VotedMessage(prevBal, id)).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public void sendSuccess(Decree decree) {
        remote.connection.submit(logit(new SuccessMessage(decree)).serialize(), this, Duration.ofSeconds(5));
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
