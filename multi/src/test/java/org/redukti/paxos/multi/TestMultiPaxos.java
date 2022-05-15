package org.redukti.paxos.multi;

import org.junit.Assert;
import org.junit.Test;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.BallotedDecree;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.net.api.RequestResponseSender;
import org.redukti.paxos.net.impl.CorrelationId;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestMultiPaxos {

    int myId = 0;
    Ledger ledger = new MockLedger(myId);
    ThisPaxosParticipant me = new ThisPaxosParticipant(myId, ledger);
    Ledger r1ledger = new MockLedger(1);
    MockRemoteParticipant remote1 = new MockRemoteParticipant(1, r1ledger);
    Ledger r2ledger = new MockLedger(2);
    MockRemoteParticipant remote2 = new MockRemoteParticipant(2, r2ledger);

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
        Assert.assertEquals(remote1, new MockRemoteParticipant(remote1.getId(), r1ledger));
        Assert.assertEquals(2, me.quorumSize());
        Assert.assertEquals(Status.IDLE, me.status);
    }

    @Test
    public void testCreateEvenParticipants() {
        List<PaxosParticipant> remotes = List.of(remote1, remote2, new MockRemoteParticipant(3, null));
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


    // Scenario
    // we have 2 committed outcomes.
    // remote 1 has higher cnum, responds to prepare (we expect updates from remote 1)
    // remote 2 has lower cnum, responds to accept (we expect pending vote)
    // select a new value - going through phase 1
    // select another value - skipping phase 1
    @Test
    public void testWithNoPriorBallot() {
        List<MockRemoteParticipant> remotes = List.of(remote1, remote2);
        me.addRemotes(remotes);
        Assert.assertEquals(2, me.quorumSize());

        ledger.setOutcome(0, 101);
        ledger.setOutcome(1, 102);
        Assert.assertEquals(1, ledger.getCommitNum());

        r1ledger.setOutcome(0, 101);
        r1ledger.setOutcome(1, 102);
        r1ledger.setOutcome(2, 103);
        r1ledger.setOutcome(3, 104);

        // These are acceptors so they actually only need to message me
        remote1.addRemotes(List.of(me, remote2));
        remote2.addRemotes(List.of(me, remote1));

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
            Assert.assertEquals(ledger.getLastTried(), remoteParticipant.nextBallotMessages.get(0).b);
        }
        Assert.assertEquals(ledger.getLastTried(), ledger.getMaxBal());
        Assert.assertEquals(0, me.prevVotes.size()); // Because am not participating in ballots, so no vote
        Assert.assertEquals(1, me.prevVoters.size());
        Assert.assertTrue(me.prevVoters.containsKey(myId));

        remote1.receiveNextBallot(remote1.nextBallotMessages.get(0));
        assertEquals(ledger.getCommitNum(), r1ledger.getCommitNum());
        Assert.assertEquals(Status.POLLING, me.status);
        Assert.assertEquals(1, remote1.beginBallotMessages.size());
        Assert.assertEquals(0, remote1.beginBallotMessages.get(0).committedDecrees.length);
        Assert.assertEquals(1, remote1.beginBallotMessages.get(0).chosenDecrees.length);
        Assert.assertEquals(4, remote1.beginBallotMessages.get(0).chosenDecrees[0].decreeNum);
        Assert.assertEquals(42, remote1.beginBallotMessages.get(0).chosenDecrees[0].value);
        Assert.assertEquals(1, remote2.beginBallotMessages.size());
        Assert.assertEquals(0, remote2.beginBallotMessages.get(0).committedDecrees.length);
        Assert.assertEquals(1, remote2.beginBallotMessages.get(0).chosenDecrees.length);
        Assert.assertEquals(4, remote2.beginBallotMessages.get(0).chosenDecrees[0].decreeNum);
        Assert.assertEquals(42, remote2.beginBallotMessages.get(0).chosenDecrees[0].value);

        remote2.receiveBeginBallot(remote2.beginBallotMessages.get(0));
        Assert.assertEquals(1, remote1.beginBallotMessages.size());
        Assert.assertEquals(2, remote2.beginBallotMessages.size());
        Assert.assertEquals(1, remote2.beginBallotMessages.get(1).chosenDecrees.length);
        Assert.assertEquals(4, remote2.beginBallotMessages.get(1).chosenDecrees[0].decreeNum);
        Assert.assertEquals(42, remote2.beginBallotMessages.get(1).chosenDecrees[0].value);
        Assert.assertEquals(4, remote2.beginBallotMessages.get(1).committedDecrees.length);

        remote2.receiveBeginBallot(remote2.beginBallotMessages.get(1));
        Assert.assertEquals(4, ledger.getCommitNum());
        Assert.assertEquals(1, remote1.successMessages.size());
        Assert.assertEquals(1, remote2.successMessages.size());
        remote1.receiveSuccess(remote1.successMessages.get(0));
        remote2.receiveSuccess(remote2.successMessages.get(0));
        Assert.assertEquals(4, r1ledger.getCommitNum());
        Assert.assertEquals(4, r2ledger.getCommitNum());
        Assert.assertEquals(Long.valueOf(42), ledger.getOutcome(4));
        Assert.assertEquals(Long.valueOf(42), r1ledger.getOutcome(4));
        Assert.assertEquals(Long.valueOf(42), r2ledger.getOutcome(4));
        Assert.assertEquals(1, responseSender.responses.size());
        ClientResponseMessage cra = (ClientResponseMessage) PaxosMessages.parseMessage(crm.correlationId, responseSender.responses.get(0));
        Assert.assertEquals(42, cra.agreedValue);
        Assert.assertEquals(4, cra.dnum);
        Assert.assertEquals(0, me.prevVotes.size());

        ClientRequestMessage crm2 = new ClientRequestMessage(new CorrelationId(3,2), 142);
        me.receiveClientRequest(responseSender, crm2);
        remote1.receiveBeginBallot(remote1.beginBallotMessages.get(1));
        remote1.receiveSuccess(remote1.successMessages.get(1));
        remote2.receiveSuccess(remote2.successMessages.get(1));
        ClientResponseMessage cra2 = (ClientResponseMessage) PaxosMessages.parseMessage(crm.correlationId, responseSender.responses.get(1));
        Assert.assertEquals(142, cra2.agreedValue);
        Assert.assertEquals(5, cra2.dnum);
        Assert.assertEquals(0, me.prevVotes.size());
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

    static final class MockRemoteParticipant extends ThisPaxosParticipant {

        List<NextBallotMessage> nextBallotMessages = new ArrayList<>();
        List<BeginBallotMessage> beginBallotMessages = new ArrayList<>();
        List<SuccessMessage> successMessages = new ArrayList<>();

        public MockRemoteParticipant(int id, Ledger ledger) {
            super(id, ledger);
        }

        @Override
        public void sendNextBallot(BallotNum b, int pid, long commitNum) {
            nextBallotMessages.add(new NextBallotMessage(b,pid, commitNum));
        }

        @Override
        public void sendLastVoteMessage(BallotNum b, int pid, long cnum, Vote[] votes) {
        }

        @Override
        public void sendBeginBallot(BallotNum b, int pid, long cnum, Decree[] chosenDecrees, Decree[] committedDecrees) {
            beginBallotMessages.add(new BeginBallotMessage(b, pid, cnum, chosenDecrees, committedDecrees));
        }

        @Override
        public void sendPendingVote(BallotNum b, int pid, long cnum) {

        }

        @Override
        public void sendVoted(BallotNum prevBal, int id) {

        }

        @Override
        public void sendSuccess(Decree[] decrees) {
            successMessages.add(new SuccessMessage(decrees));
        }
    }

    static final class MockLedger implements Ledger {

        final int id;

        final Map<Long, Long> outcomes = new HashMap<>();
        final Map<Long, Pair<BallotNum, Decree>> inflightBallots = new HashMap<>();
        BallotNum lastTried;
        BallotNum nextBal;
        long commitNum;

        public MockLedger(int id) {
            this.id = id;
            this.lastTried = new BallotNum(-1, id);
            this.nextBal = new BallotNum(-1, id);
            this.commitNum = -1;
        }

        @Override
        public void setOutcome(long decreeNum, long data) {
            outcomes.put(decreeNum, data);
            inflightBallots.remove(decreeNum);
            if (decreeNum == commitNum+1) {
                commitNum = decreeNum;
                while (outcomes.containsKey(commitNum+1)) {
                    commitNum++;
                }
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
        public void setPrevBallot(BallotNum ballot, long dnum, long value) {
            if (outcomes.containsKey(dnum)) {
                throw new IllegalArgumentException();
            }
            inflightBallots.put(dnum, new Pair<>(ballot, new Decree(dnum, value)));
        }

        @Override
        public BallotNum getPrevBallot(long dnum) {
            Pair<BallotNum, Decree> pair = inflightBallots.get(dnum);
            if (pair == null)
                return new BallotNum(-1, id);
            return pair.first;
        }

        @Override
        public Decree getPrevDec(long dnum) {
            Pair<BallotNum, Decree> pair = inflightBallots.get(dnum);
            if (pair == null)
                return new Decree(-1, 0);
            return pair.second;
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

        @Override
        public long getCommitNum() {
            return commitNum;
        }

        @Override
        public List<BallotedDecree> getUndecidedBallots() {
            return inflightBallots.values().stream().map(e -> new BallotedDecree(e.first, e.second)).collect(Collectors.toList());
        }
    }
}
