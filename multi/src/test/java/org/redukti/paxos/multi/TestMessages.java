package org.redukti.paxos.multi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.net.impl.CorrelationId;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class TestMessages {

    CorrelationId correlationId = new CorrelationId(1,101);
    BallotNum b = new BallotNum(39, 3);
    Decree d = new Decree(13, 94);
    Decree d2 = new Decree(14, 76);
    Vote v = new Vote(3, b, d);
    Vote v2 = new Vote(3, b, d2);

    @Test
    public void testClientRequestMessage() {
        ClientRequestMessage crm = new ClientRequestMessage(correlationId, 42);
        ByteBuffer bb = crm.serialize();
        ClientRequestMessage crm2 = (ClientRequestMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(crm.requestedValue, crm2.requestedValue);
    }

//    @Test
//    public void testClientRessponeMessage() {
//        ClientResponseMessage crm = new ClientResponseMessage(42);
//        ByteBuffer bb = crm.serialize();
//        ClientResponseMessage crm2 = (ClientResponseMessage) PaxosMessages.parseMessage(correlationId, bb);
//        Assertions.assertEquals(crm.agreedValue, crm2.agreedValue);
//    }

    @Test
    public void testNextBallotMessage() {
        NextBallotMessage m = new NextBallotMessage(b, 1, 100);
        Assertions.assertEquals(1, m.pid);
        Assertions.assertEquals(100, m.cnum);
        ByteBuffer bb = m.serialize();
        NextBallotMessage m2 = (NextBallotMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.b, m2.b);
        Assertions.assertEquals(1, m2.pid);
        Assertions.assertEquals(100, m2.cnum);
    }

    @Test
    public void testLastVoteMessage() {

        LastVoteMessage m = new LastVoteMessage(b, 3, 101, new Vote[]{v, v2});
        ByteBuffer bb = m.serialize();
        LastVoteMessage m2 = (LastVoteMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.b, m2.b);
        Assertions.assertEquals(3, m.pid);
        Assertions.assertEquals(101, m.cnum);
        Assertions.assertEquals(2, m.votes.length);
        Assertions.assertTrue(Arrays.equals(m.votes, m2.votes));
        Assertions.assertEquals(m.pid, m2.pid);
        Assertions.assertEquals(m.cnum, m2.cnum);
    }

    @Test
    public void testBeginBallotMessage() {
        BeginBallotMessage m = new BeginBallotMessage(b, 3, 101, new Decree[]{d, d2});
        ByteBuffer bb = m.serialize();
        BeginBallotMessage m2 = (BeginBallotMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.b, m2.b);
        Assertions.assertEquals(3, m.pid);
        Assertions.assertEquals(101, m.cnum);
        Assertions.assertEquals(2, m.chosenDecrees.length);
        Assertions.assertTrue(Arrays.equals(m.chosenDecrees, m2.chosenDecrees));
        Assertions.assertEquals(m.pid, m2.pid);
        Assertions.assertEquals(m.cnum, m2.cnum);
    }
//
//    @Test
//    public void testVotedMessage() {
//        VotedMessage m = new VotedMessage(b, 3);
//        ByteBuffer bb = m.serialize();
//        VotedMessage m2 = (VotedMessage) PaxosMessages.parseMessage(correlationId, bb);
//        Assertions.assertEquals(m.b, m2.b);
//        Assertions.assertEquals(m.owner, m2.owner);
//    }

    @Test
    public void testSuccessMessage() {
        Decree[] decrees = new Decree[]{d, d2};
        SuccessMessage m = new SuccessMessage(decrees);
        ByteBuffer bb = m.serialize();
        SuccessMessage m2 = (SuccessMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.decree.length, m2.decree.length);
        Assertions.assertEquals(m.decree[0], m2.decree[0]);
        Assertions.assertEquals(m.decree[1], m2.decree[1]);
    }

}
