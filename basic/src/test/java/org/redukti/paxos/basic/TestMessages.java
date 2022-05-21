package org.redukti.paxos.basic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.net.impl.CorrelationId;

import java.nio.ByteBuffer;

public class TestMessages {

    CorrelationId correlationId = new CorrelationId(1,101);
    BallotNum b = new BallotNum(39, 3);
    Decree d = new Decree(13, 94);
    Vote v = new Vote(3, b, d);

    @Test
    public void testClientRequestMessage() {
        ClientRequestMessage crm = new ClientRequestMessage(correlationId, 42);
        ByteBuffer bb = crm.serialize();
        ClientRequestMessage crm2 = (ClientRequestMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(crm.requestedValue, crm2.requestedValue);
    }

    @Test
    public void testClientRessponeMessage() {
        ClientResponseMessage crm = new ClientResponseMessage(42);
        ByteBuffer bb = crm.serialize();
        ClientResponseMessage crm2 = (ClientResponseMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(crm.agreedValue, crm2.agreedValue);
    }

    @Test
    public void testNextBallotMessage() {
        NextBallotMessage m = new NextBallotMessage(b);
        ByteBuffer bb = m.serialize();
        NextBallotMessage m2 = (NextBallotMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.b, m2.b);
    }

    @Test
    public void testLastVoteMessage() {
        LastVoteMessage m = new LastVoteMessage(b, v);
        ByteBuffer bb = m.serialize();
        LastVoteMessage m2 = (LastVoteMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.b, m2.b);
        Assertions.assertEquals(m.v, m2.v);
        Assertions.assertEquals(m.v.decree, m2.v.decree);
    }

    @Test
    public void testBeginBallotMessage() {
        BeginBallotMessage m = new BeginBallotMessage(b, d);
        ByteBuffer bb = m.serialize();
        BeginBallotMessage m2 = (BeginBallotMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.b, m2.b);
        Assertions.assertEquals(m.decree, m2.decree);
    }

    @Test
    public void testVotedMessage() {
        VotedMessage m = new VotedMessage(b, 3);
        ByteBuffer bb = m.serialize();
        VotedMessage m2 = (VotedMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.b, m2.b);
        Assertions.assertEquals(m.owner, m2.owner);
    }

    @Test
    public void testSuccessMessage() {
        SuccessMessage m = new SuccessMessage(d);
        ByteBuffer bb = m.serialize();
        SuccessMessage m2 = (SuccessMessage) PaxosMessages.parseMessage(correlationId, bb);
        Assertions.assertEquals(m.decree, m2.decree);
    }

}
