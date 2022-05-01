package org.redukti.paxos.basic;

import ch.qos.logback.classic.AsyncAppender;
import com.sun.source.tree.ClassTree;
import org.junit.Assert;
import org.junit.Test;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.net.api.RequestResponseSender;
import org.redukti.paxos.net.impl.CorrelationId;

import java.nio.ByteBuffer;
import java.util.*;

import static org.junit.Assert.fail;

public class TestBasicPaxos {

    int myId = 0;
    Ledger ledger = new MockLedger(myId);
    ThisPaxosParticipant me = new ThisPaxosParticipant(myId, ledger);
    MockRemoteParticipant remote1 = new MockRemoteParticipant(1);
    MockRemoteParticipant remote2 = new MockRemoteParticipant(2);

    @Test
    public void testCreate() {
        Assert.assertEquals(myId, me.getId());
        Assert.assertEquals(1, me.all.size());
        Assert.assertNotNull(me.findParticipant(myId));
        Assert.assertEquals(me, me.findParticipant(myId));
        Assert.assertEquals(me, me);
        List<PaxosParticipant> remotes = List.of(remote1, remote2);
        me.addRemotes(remotes);
        Assert.assertEquals(3, me.all.size());
        Assert.assertEquals(me, me.findParticipant(myId));
        Assert.assertEquals(remote1, me.findParticipant(1));
        Assert.assertEquals(remote2, me.findParticipant(2));
        Assert.assertNotEquals(me, remote1);
        Assert.assertNotEquals(me, remote2);
        Assert.assertEquals(remote1, new MockRemoteParticipant(remote1.getId()));
        Assert.assertEquals(2, me.quorumSize());
        Assert.assertEquals(Status.IDLE, me.status);
    }

    @Test
    public void testCreateEvenParticipants() {
        List<PaxosParticipant> remotes = List.of(remote1, remote2, new MockRemoteParticipant(3));
        try {
            me.addRemotes(remotes);
            fail();
        }
        catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testCreateTooFewParticipants() {
        List<PaxosParticipant> remotes = List.of(remote1);
        try {
            me.addRemotes(remotes);
            fail();
        }
        catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testReceiveClientRequest() {
        List<MockRemoteParticipant> remotes = List.of(remote1, remote2);
        me.addRemotes(remotes);
        Assert.assertEquals(2, me.quorumSize());

        ClientRequestMessage crm = new ClientRequestMessage(new CorrelationId(3,1), 42);
        MockResponseSender responseSender = new MockResponseSender();
        BallotNum prevTried = ledger.getLastTried();
        Assert.assertTrue(prevTried.isNull());
        me.receiveClientRequest(responseSender, crm);
        Assert.assertEquals(crm, me.currentRequest);
        Assert.assertEquals(responseSender, me.currentResponseSender);
        Assert.assertEquals(Status.TRYING, me.status);
        Assert.assertEquals(prevTried.increment(), ledger.getLastTried());
        for (MockRemoteParticipant remoteParticipant: remotes) {
            Assert.assertEquals(1, remoteParticipant.nextBallotMessages.size());
            Assert.assertEquals(ledger.getLastTried(), remoteParticipant.nextBallotMessages.get(0));
        }
        Assert.assertEquals(ledger.getLastTried(), ledger.getMaxBal());
        Assert.assertEquals(1, me.prevVotes.size());
        Assert.assertTrue(containsVote(me.prevVotes, 0, new BallotNum(-1, 0),
                new Decree(-1, 0)));
        // quorum not reached
        for (MockRemoteParticipant remoteParticipant: remotes) {
            Assert.assertEquals(0, remoteParticipant.ballotsStarted.size());
        }
    }

    boolean containsVote(Set<Vote> votes, int process, BallotNum b, Decree d) {
        Vote v = new Vote(process, b, d);
        return votes.contains(v);
    }

    static final class MockResponseSender implements RequestResponseSender {

        @Override
        public void setData(ByteBuffer data) {

        }

        @Override
        public void setErrored(String errorMessage) {

        }

        @Override
        public void submit() {

        }
    }

    static final class Pair<T1,T2> {
        public final T1 first;
        public final T2 second;
        public Pair(T1 t1, T2 t2) {
            this.first = t1;
            this.second = t2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?, ?> pair = (Pair<?, ?>) o;
            return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
    }

    static final class MockRemoteParticipant extends PaxosParticipant {

        final int myId;
        List<BallotNum> nextBallotMessages = new ArrayList<>();
        List<Pair<BallotNum, Vote>> votes = new ArrayList<>();
        List<Pair<BallotNum, Decree>> ballotsStarted = new ArrayList<>();

        public MockRemoteParticipant(int myId) {
            this.myId = myId;
        }

        @Override
        public int getId() {
            return myId;
        }

        @Override
        public void sendNextBallot(BallotNum b) {
            nextBallotMessages.add(b);
        }

        @Override
        public void sendLastVoteMessage(BallotNum b, Vote v) {
            votes.add(new Pair<>(b, v));
        }

        @Override
        public void sendBeginBallot(BallotNum b, Decree decree) {

        }

        @Override
        public void sendVoted(BallotNum prevBal, int id) {

        }

        @Override
        public void sendSuccess(Decree decree) {

        }
    }

    static final class MockLedger implements Ledger {

        final int id;

        final Map<Long, Long> outcomes = new HashMap<>();
        BallotNum lastTried;
        BallotNum prevBal;
        Decree prevDecree;
        BallotNum nextBal;

        public MockLedger(int id) {
            this.id = id;
            this.lastTried = new BallotNum(-1, id);
            this.prevBal = new BallotNum(-1, id);
            this.prevDecree = new Decree(-1, 0);
            this.nextBal = new BallotNum(-1, id);
        }

        @Override
        public void setOutcome(long decreeNum, long data) {
            outcomes.put(decreeNum, data);
        }

        @Override
        public Long getOutcome(long decreeNum) {
            return outcomes.get(decreeNum);
        }

        @Override
        public void setLastTried(BallotNum ballot) {
            lastTried = ballot;
        }

        @Override
        public BallotNum getLastTried() {
            return lastTried;
        }

        @Override
        public void setPrevBallot(BallotNum ballot) {
            prevBal = ballot;
        }

        @Override
        public BallotNum getPrevBallot() {
            return prevBal;
        }

        @Override
        public void setPrevDec(Decree decree) {
            prevDecree = decree;
        }

        @Override
        public Decree getPrevDec() {
            return prevDecree;
        }

        @Override
        public void setNextBallot(BallotNum ballot) {
            this.nextBal = ballot;
        }

        @Override
        public BallotNum getNextBallot() {
            return nextBal;
        }

        @Override
        public void close() {
        }
    }
}
