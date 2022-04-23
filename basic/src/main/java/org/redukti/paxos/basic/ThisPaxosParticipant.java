package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ThisPaxosParticipant extends PaxosParticipant implements RequestHandler {

    final static Logger log = LoggerFactory.getLogger(ThisPaxosParticipant.class);

    final int myId;
    final Ledger ledger;

    // Status of the process, initial IDLE
    Status status = Status.IDLE;
    // If status == POLLING, then the set of quorum members from whom
    // we have received Voted messages in the current ballot; otherwise, meaningless.
    Set<PaxosParticipant> voters = new LinkedHashSet<>();
    // quorum
    Set<PaxosParticipant> quorum = new LinkedHashSet<>();
    Set<Vote> prevVotes = new LinkedHashSet<>();
    Set<PaxosParticipant> all = new LinkedHashSet<>();
    Decree decree = null;

    static final long DECREE_NUM = 0;

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

    public synchronized void tryNewBallot() {
        BallotNum b = ledger.getLastTried(DECREE_NUM);
        b = b.increment();
        ledger.setLastTried(DECREE_NUM,b);
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
    public synchronized void handleRequest(Message request, Message response) {
        PaxosMessage pm = PaxosMessages.parseMessage(request.getData());
        log.info("Received " + pm.toString());
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
        else {
            log.error("Unknown message " + pm);
        }
    }

    // Receive NextBallot (b) Message
    // If b ≥ nextBal [p] then
    // – Set nextBal [p] to b.
    // Send LastVote Message
    // Enabled whenever nextBal[p] > prevBal [p].
    // – Send a LastVote(nextBal [p], v) message to priest owner(nextBal [p]), where
    //   vpst = p, vbal = prevBal [p], and vdec = prevDec[p].
    void receiveNextBallot(NextBallotMessage pm) {
        BallotNum b = pm.b;
        BallotNum nextBal = ledger.getNextBallot(DECREE_NUM);
        if (b.compareTo(nextBal) >= 0) {
            ledger.setNextBallot(DECREE_NUM,b);
            nextBal = b;
        }
        BallotNum prevBal = ledger.getPrevBallot(DECREE_NUM);
        if (nextBal.compareTo(prevBal) > 0) {
            int owner = b.processNum; // process that sent us NextBallotMessage
            PaxosParticipant p = findParticipant(owner);
            // v is the vote with the largest ballot number
            // less than b that we have cast, or its null if we haven't yet
            // voted in a ballot less than b.
            Vote v = new Vote(myId, prevBal, ledger.getPrevDec(DECREE_NUM));
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

    // Receive LastVote(b, v) Message
    // If b = lastTried [p] and status[p] = trying, then
    // – Set prevVotes[p] to the union of its original value and {v}.
    void receiveLastVote(LastVoteMessage lv) {
        BallotNum b = lv.b;
        BallotNum lastTried = ledger.getLastTried(DECREE_NUM);
        if (b.equals(lastTried) && status == Status.TRYING) {
            prevVotes.add(lv.v);
            if (prevVotes.size() >= 2) {
                startPolling();
            }
        }
    }

    void startPolling() {
        status = Status.POLLING;
        quorum = prevVotes.stream().map(v -> findParticipant(v.process)).collect(Collectors.toSet());
        voters.clear();
        Vote maxVote = prevVotes.stream().sorted((a,b) -> b.compareTo(a)).findFirst().get();
        Decree maxVoteDecree = maxVote.decree;
        if (maxVoteDecree.isNull())
            // Choose any decree
            decree = new Decree(DECREE_NUM, 42);
        else
            decree = maxVoteDecree;
        beginBallot();
    }

    void beginBallot() {
        assert status == Status.POLLING;
        BallotNum b = ledger.getLastTried(DECREE_NUM);
        for (PaxosParticipant p: quorum) {
            p.sendBeginBallot(b, decree);
        }
    }

    void receiveBeginBallot(BeginBallotMessage pm) {
        BallotNum b = pm.b;
        BallotNum nextBal = ledger.getNextBallot(DECREE_NUM);
        if (b.equals(nextBal)) {
            BallotNum prevBal = ledger.getPrevBallot(DECREE_NUM);
            if (b.compareTo(prevBal) > 0) {
                ledger.setPrevBallot(DECREE_NUM,b);
                ledger.setPrevDec(DECREE_NUM, pm.decree);
                PaxosParticipant p = findParticipant(b.processNum);
                p.sendVoted(b, myId);
            }
        }
    }

    void receiveVoted(VotedMessage vm) {
        BallotNum lastTried = ledger.getLastTried(DECREE_NUM);
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
        Long v = ledger.getOutcome(sm.decree.decreeNum);
        if (v == null) {
            ledger.setOutcome(sm.decree.decreeNum, sm.decree.value);
        }
    }
}
