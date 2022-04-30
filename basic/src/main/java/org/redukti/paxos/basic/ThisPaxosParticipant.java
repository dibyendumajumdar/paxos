package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.redukti.paxos.net.api.RequestResponseSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ThisPaxosParticipant extends PaxosParticipant implements RequestHandler {

    final static Logger log = LoggerFactory.getLogger(ThisPaxosParticipant.class);

    final int myId;
    final Ledger ledger;

    // Status of the process, initial IDLE
    volatile Status status = Status.IDLE;
    // If status == POLLING, then the set of quorum members from whom
    // we have received Voted messages in the current ballot; otherwise, meaningless.
    Set<PaxosParticipant> voters = new LinkedHashSet<>();
    // quorum
    Set<PaxosParticipant> quorum = new LinkedHashSet<>();
    Set<Vote> prevVotes = new LinkedHashSet<>();
    Set<PaxosParticipant> all = new LinkedHashSet<>();
    Decree decree = null;

    AtomicReference<ClientRequestMessage> currentRequest = new AtomicReference<>();
    volatile RequestResponseSender currentResponseSender;

    final BasicPaxosProcess process;

    public ThisPaxosParticipant(BasicPaxosProcess process) {
        this.process = process;
        this.ledger = process.ledger;
        this.myId = process.myId;
        all.add(this);
    }

    public synchronized void addRemotes() {
        for (ProcessChannel p: process.remoteProcesses) {
            all.add(new RemotePaxosParticipant(p.id, p));
        }
    }

    /**
     * Also known as Phase1a(b)
     * In the Phase1a(b) action, it sends to all acceptors a phase 1a message that begins ballot b.
     */
    public synchronized void tryNewBallot() {
        if (status != Status.IDLE) {
            return;
        }
        BallotNum b = ledger.getLastTried();
        b = b.increment();
        ledger.setLastTried(b);
        status = Status.TRYING;
        prevVotes.clear();
        nextBallot(b);
    }

    void nextBallot(BallotNum b) {
        for (PaxosParticipant p: all) {
            p.sendNextBallot(b);
        }
    }

    @Override
    public int getId() {
        return myId;
    }

    @Override
    public void sendNextBallot(BallotNum b) {
        receiveNextBallot(new NextBallotMessage(b));
    }

    @Override
    public void sendLastVoteMessage(BallotNum b, Vote v) {
        receiveLastVote(new LastVoteMessage(b, v));
    }

    @Override
    public void sendBeginBallot(BallotNum b, Decree decree) {
        receiveBeginBallot(new BeginBallotMessage(b, decree));
    }

    @Override
    public void sendVoted(BallotNum prevBal, int id) {
        receiveVoted(new VotedMessage(prevBal, id));
    }

    @Override
    public void sendSuccess(Decree decree) {
        receiveSuccess(new SuccessMessage(decree));
    }

    @Override
    public synchronized void handleRequest(Message request, RequestResponseSender responseSender) {
        PaxosMessage pm = PaxosMessages.parseMessage(request.getCorrelationId(), request.getData());
        if (pm instanceof NextBallotMessage) {
            receiveNextBallot((NextBallotMessage) pm);
        }
        else if (pm instanceof LastVoteMessage) {
            receiveLastVote((LastVoteMessage) pm);
        }
        else if (pm instanceof BeginBallotMessage) {
            receiveBeginBallot((BeginBallotMessage) pm);
        }
        else if (pm instanceof VotedMessage) {
            receiveVoted((VotedMessage) pm);
        }
        else if (pm instanceof SuccessMessage) {
            receiveSuccess((SuccessMessage) pm);
        }
        else if (pm instanceof ClientRequestMessage) {
            receiveClientRequest(responseSender, (ClientRequestMessage) pm);
        }
        else {
            log.error("Unknown message " + pm);
        }
    }

    void receiveClientRequest(RequestResponseSender responseSender, ClientRequestMessage pm) {
        log.info("Received " + pm);
        if (currentRequest.compareAndSet(null, pm)) {
            this.currentResponseSender = responseSender;
            tryNewBallot();
        }
    }

    // Also known as Phase1b(a)
    // Some conflicting description in PTP:
    // p26.
    // Receive NextBallot (b) Message
    // If b ≥ nextBal [p] then
    // – Set nextBal [p] to b.
    // Send LastVote Message
    // Enabled whenever nextBal[p] > prevBal [p].
    // – Send a LastVote(nextBal [p], v) message to priest owner(nextBal [p]), where
    //   vpst = p, vbal = prevBal [p], and vdec = prevDec[p].
    //
    // p12.
    // Upon receipt of a NextBallot(b) mesage from p with b > nextBal[q],
    // priest q sets nextBal[q] to b and sends a LastVote(b,v) message to p,
    // where v equals prevVote[q]. (A NextBallot(b) message is ignored if b <= nextBal[q].)
    //
    // From PMS paper:
    // Upon receipt of a ballot b phase 1a message, acceptor a can perform
    // a Phase1b(a) action only if b > maxBal[a]. The action sets maxBal[a] to b
    // and sends a phase 1b message to the leader containing the values of
    // maxVBal[a] and maxVal[a].
    void receiveNextBallot(NextBallotMessage pm) {
        log.info("Received " + pm);
        BallotNum b = pm.b;
        BallotNum maxBal = ledger.getMaxBal();
        if (b.compareTo(maxBal) > 0) {
            ledger.setMaxBal(b);
            int owner = b.processNum; // process that sent us NextBallotMessage
            PaxosParticipant p = findParticipant(owner);
            // v is the vote with the largest ballot number
            // that we have cast, or its null if we haven't yet
            // voted in a ballot.
            Vote v = new Vote(myId, ledger.getMaxVBal(), ledger.getMaxVal());
            p.sendLastVoteMessage(b, v);
        }
    }

    PaxosParticipant findParticipant(int owner) {
        for (PaxosParticipant p: all) {
            if (p.getId() == owner)
                return p;
        }
        throw new IllegalArgumentException();
    }

    // Also known as Phase2a(b,v)
    // Receive LastVote(b, v) Message
    // If b = lastTried [p] and status[p] = trying, then
    // – Set prevVotes[p] to the union of its original value and {v}.
    void receiveLastVote(LastVoteMessage lv) {
        log.info("Received " + lv);
        BallotNum b = lv.b;
        BallotNum lastTried = ledger.getLastTried();
        if (b.equals(lastTried) && status == Status.TRYING) {
            prevVotes.add(lv.v);
            if (prevVotes.size() >= 2) {
                startPolling();
            }
        }
    }

    // Part of Phase2a(b,v)
    void startPolling() {
        status = Status.POLLING;
        quorum = prevVotes.stream().map(v -> findParticipant(v.process)).collect(Collectors.toSet());
        voters.clear();
        Vote maxVote = prevVotes.stream().sorted((a,b) -> b.compareTo(a)).findFirst().get();
        Decree maxVoteDecree = maxVote.decree;
        if (maxVoteDecree.isNull()) {
            // Choose client requested value
            ClientRequestMessage crm = currentRequest.get();
            if (crm == null) {
                // hmm client timed out?
                abort();
                return;
            }
            decree = new Decree(0, crm.requestedValue);
        }
        else
            decree = maxVoteDecree;
        beginBallot();
    }

    void abort() {
        status = Status.IDLE;
        quorum.clear();
        voters.clear();
    }

    /**
     * Part of Phase2a(b,v)
     */
    void beginBallot() {
        assert status == Status.POLLING;
        BallotNum b = ledger.getLastTried();
        for (PaxosParticipant p: quorum) {
            p.sendBeginBallot(b, decree);
        }
    }

    /**
     * Also known as Phase2b(a)
     */
    void receiveBeginBallot(BeginBallotMessage pm) {
        log.info("Received " + pm);
        BallotNum b = pm.b;
        BallotNum maxBal = ledger.getMaxBal();
        if (b.equals(maxBal)) {
            BallotNum maxVBal = ledger.getMaxVBal();
            if (b.compareTo(maxVBal) > 0) {
                ledger.setMaxVBal(b);
                ledger.setMaxVal(pm.decree);
                PaxosParticipant p = findParticipant(b.processNum);
                p.sendVoted(b, myId);
            }
        }
    }

    void receiveVoted(VotedMessage vm) {
        log.info("Received " + vm);
        BallotNum lastTried = ledger.getLastTried();
        BallotNum b = vm.b;
        if (b.equals(lastTried) && status == Status.POLLING) {
            PaxosParticipant q = findParticipant(vm.owner);
            voters.add(q);
            if (voters.containsAll(quorum)) {
                Long v = ledger.getOutcome(decree.decreeNum);
                if (v == null) {
                    ledger.setOutcome(decree.decreeNum, decree.value);
                }
                for (PaxosParticipant p: all) {
                    p.sendSuccess(new Decree(decree.decreeNum, decree.value));
                }
            }
        }
    }

    void receiveSuccess(SuccessMessage sm) {
        log.info("Received " + sm);
        Long v = ledger.getOutcome(sm.decree.decreeNum);
        if (v == null) {
            ledger.setOutcome(sm.decree.decreeNum, sm.decree.value);
        }
        ClientRequestMessage crm = currentRequest.get();
        if (crm != null) {
            ClientResponseMessage rm = new ClientResponseMessage(v);
            currentResponseSender.setData(rm.serialize());
            currentResponseSender.submit();
            currentResponseSender = null;
            currentRequest.getAndSet(null);
        }
        status = Status.IDLE;
    }
}
