package org.redukti.paxos.basic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.BallotedDecree;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.net.api.RequestResponseSender;
import org.redukti.paxos.net.impl.CorrelationId;

import java.nio.ByteBuffer;
import java.util.*;

public class TestBasicPaxos {

    int myId = 0;
    Ledger ledger = new MockLedger(myId);
    ThisPaxosParticipant me = new ThisPaxosParticipant(myId, ledger);
    MockRemoteParticipant remote1 = new MockRemoteParticipant(1);
    MockRemoteParticipant remote2 = new MockRemoteParticipant(2);

    @Test
    public void testCreate() {
        Assertions.assertEquals(myId, me.getId());
        Assertions.assertEquals(1, me.all.size());
        Assertions.assertNotNull(me.findParticipant(myId));
        Assertions.assertEquals(me, me.findParticipant(myId));
        Assertions.assertEquals(me, me);
        List<PaxosParticipant> remotes = List.of(remote1, remote2);
        me.addRemotes(remotes);
        Assertions.assertEquals(3, me.all.size());
        Assertions.assertEquals(me, me.findParticipant(myId));
        Assertions.assertEquals(remote1, me.findParticipant(1));
        Assertions.assertEquals(remote2, me.findParticipant(2));
        Assertions.assertNotEquals(me, remote1);
        Assertions.assertNotEquals(me, remote2);
        Assertions.assertEquals(remote1, new MockRemoteParticipant(remote1.getId()));
        Assertions.assertEquals(2, me.quorumSize());
        Assertions.assertEquals(Status.IDLE, me.status);
    }

    @Test
    public void testCreateEvenParticipants() {
        List<PaxosParticipant> remotes = List.of(remote1, remote2, new MockRemoteParticipant(3));
        try {
            me.addRemotes(remotes);
            Assertions.fail();
        }
        catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testCreateTooFewParticipants() {
        List<PaxosParticipant> remotes = List.of(remote1);
        try {
            me.addRemotes(remotes);
            Assertions.fail();
        }
        catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testWithNoPriorBallot() {
        List<MockRemoteParticipant> remotes = List.of(remote1, remote2);
        me.addRemotes(remotes);
        Assertions.assertEquals(2, me.quorumSize());

        ClientRequestMessage crm = new ClientRequestMessage(new CorrelationId(3, 1), 42);
        MockResponseSender responseSender = new MockResponseSender();
        BallotNum prevTried = ledger.getLastTried();
        Assertions.assertTrue(prevTried.isNull());
        Assertions.assertNull(ledger.getOutcome(0));
        me.receiveClientRequest(responseSender, crm);
        Assertions.assertEquals(crm, me.currentRequest);
        Assertions.assertEquals(responseSender, me.currentResponseSender);
        Assertions.assertEquals(Status.TRYING, me.status);
        Assertions.assertEquals(prevTried.increment(), ledger.getLastTried());
        for (MockRemoteParticipant remoteParticipant : remotes) {
            Assertions.assertEquals(1, remoteParticipant.nextBallotMessages.size());
            Assertions.assertEquals(ledger.getLastTried(), remoteParticipant.nextBallotMessages.get(0));
        }
        Assertions.assertEquals(ledger.getLastTried(), ledger.getMaxBal());
        Assertions.assertEquals(1, me.prevVotes.size());
        Assertions.assertTrue(containsVote(me.prevVotes, 0, new BallotNum(-1, 0),
                new Decree(-1, 0)));
        // quorum not reached
        for (MockRemoteParticipant remoteParticipant : remotes) {
            Assertions.assertEquals(0, remoteParticipant.ballotsStarted.size());
        }
        BallotNum currentballot = ledger.getLastTried();
        Assertions.assertNull(ledger.getOutcome(0));
        Vote remote1Vote = new Vote(remote1.getId(), new BallotNum(-1, remote1.getId()), new Decree(-1, 0));
        me.receiveLastVote(new LastVoteMessage(currentballot, remote1Vote));
        Assertions.assertEquals(2, me.prevVotes.size());
        Assertions.assertTrue(containsVote(me.prevVotes, remote1Vote.process, remote1Vote.ballotNum, remote1Vote.decree));
        Assertions.assertEquals(1, remote1.ballotsStarted.size());
        Assertions.assertEquals(Status.POLLING, me.status); // still waiting for VotedMessage
        // now let remote2 return Voted message
        me.receiveVoted(new VotedMessage(currentballot, remote2.getId()));
        Assertions.assertEquals(2, me.voters.size());
        Assertions.assertTrue(me.quorum.contains(me));
        Assertions.assertTrue(me.quorum.contains(remote1));
        Assertions.assertEquals(2, me.quorum.size());
        Assertions.assertTrue(me.voters.contains(me));
        Assertions.assertTrue(me.voters.contains(remote2));
        if (me.version == ThisPaxosParticipant.PART_TIME_PARLIAMENT_VERSION) {
            // not quorum yet as acceptors set not same as those who promised
            Assertions.assertEquals(Status.POLLING, me.status);
            Assertions.assertNull(ledger.getOutcome(0));
        } else {
            Assertions.assertEquals(Status.IDLE, me.status);
            Assertions.assertEquals(Long.valueOf(42), ledger.getOutcome(0));
            Assertions.assertEquals(1, responseSender.responses.size());
        }
    }

    @Test
    public void testSingleParticipantQuorum() {
        Assertions.assertEquals(1, me.quorumSize());

        CorrelationId correlationId = new CorrelationId(3, 1);
        ClientRequestMessage crm = new ClientRequestMessage(correlationId, 42);
        MockResponseSender responseSender = new MockResponseSender();
        BallotNum prevTried = ledger.getLastTried();
        Assertions.assertNull(ledger.getOutcome(0));
        Assertions.assertTrue(prevTried.isNull());
        me.receiveClientRequest(responseSender, crm);
        Assertions.assertEquals(Status.IDLE, me.status);
        Assertions.assertEquals(prevTried.increment(), ledger.getLastTried());
        Assertions.assertEquals(ledger.getLastTried(), ledger.getMaxBal());
        Assertions.assertEquals(Long.valueOf(42), ledger.getOutcome(0));
        Assertions.assertEquals(1, me.prevVotes.size());
        Assertions.assertTrue(containsVote(me.prevVotes, 0, new BallotNum(-1, 0),
                new Decree(-1, 0)));
        Assertions.assertEquals(1, me.voters.size());
        Assertions.assertTrue(me.voters.contains(me));
        Assertions.assertEquals(1, me.quorum.size());
        Assertions.assertTrue(me.quorum.contains(me));
        Assertions.assertEquals(1, responseSender.responses.size());
        prevTried = ledger.getLastTried();
        crm = new ClientRequestMessage(correlationId, 44);
        me.receiveClientRequest(responseSender, crm);
        Assertions.assertEquals(prevTried.increment(), ledger.getLastTried());
        Assertions.assertEquals(ledger.getLastTried(), ledger.getMaxBal());
        Assertions.assertEquals(Long.valueOf(42), ledger.getOutcome(0));
        Assertions.assertEquals(1, me.prevVotes.size());
        Assertions.assertTrue(containsVote(me.prevVotes, 0, prevTried,
                new Decree(0, 42)));
        Assertions.assertEquals(1, me.voters.size());
        Assertions.assertTrue(me.voters.contains(me));
        Assertions.assertEquals(1, me.quorum.size());
        Assertions.assertTrue(me.quorum.contains(me));
        Assertions.assertEquals(2, responseSender.responses.size());
        ClientResponseMessage responseMessage = (ClientResponseMessage) PaxosMessages.parseMessage(correlationId, responseSender.responses.get(1));
        Assertions.assertEquals(42, responseMessage.agreedValue); // Value does not change
    }


    boolean containsVote(Set<Vote> votes, int process, BallotNum b, Decree d) {
        Vote v = new Vote(process, b, d);
        return votes.contains(v);
    }

    static final class MockResponseSender implements RequestResponseSender {
        List<ByteBuffer> responses = new ArrayList<>();

        @Override
        public void setData(ByteBuffer data) {
            responses.add(data);
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
            ballotsStarted.add(new Pair<>(b, decree));
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
        long commitNum;

        public MockLedger(int id) {
            this.id = id;
            this.lastTried = new BallotNum(-1, id);
            this.prevBal = new BallotNum(-1, id);
            this.prevDecree = new Decree(-1, 0);
            this.nextBal = new BallotNum(-1, id);
            this.commitNum = -1;
        }

        @Override
        public void setOutcome(long decreeNum, long data) {
            outcomes.put(decreeNum, data);
            if (decreeNum > commitNum) {
                commitNum = decreeNum;
            }
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
        public void setMaxVBal(BallotNum ballot, long dnum, long value) {
            prevBal = ballot;
            prevDecree = new Decree(dnum, value);
        }

        @Override
        public BallotNum getMaxVBal(long dnum) {
            return prevBal;
        }

        @Override
        public Decree getMaxVal(long dnum) {
            return prevDecree;
        }

        @Override
        public void setMaxBal(BallotNum ballot) {
            this.nextBal = ballot;
        }

        @Override
        public BallotNum getMaxBal() {
            return nextBal;
        }

        @Override
        public void close() {
        }

        @Override
        public long getCommitNum() {
            return commitNum;
        }

        @Override
        public List<BallotedDecree> getUndecidedBallots() {
            if (outcomes.get(prevDecree.decreeNum) != null && !prevBal.isNull()) {
                return Arrays.asList(new BallotedDecree(prevBal, prevDecree));
            }
            return Collections.emptyList();
        }
    }
}
