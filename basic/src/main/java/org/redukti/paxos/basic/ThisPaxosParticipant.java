package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ThisPaxosParticipant extends PaxosParticipant implements RequestHandler {

    int id;
    Ledger ledger;

    // Status of the process, initial IDLE
    Status status;
    // If status == POLLING, then the set of quorum members from whom
    // we have received Voted messages in the current ballot; otherwise, meaningless.
    Set<PaxosParticipant> voters;
    // quorum
    Set<PaxosParticipant> quorum = new LinkedHashSet<>();
    Set<Vote> prevVotes = new LinkedHashSet<>();
    Set<PaxosParticipant> all = new LinkedHashSet<>();
    Decree decree = null;

    BasicPaxosProcess process;

    public ThisPaxosParticipant(BasicPaxosProcess process) {
        all.add(this);
    }

    public void addRemotes() {
        for (ProcessChannel p: process.remoteProcesses) {
            all.add(new RemotePaxosParticipant(p.id, p));
        }
    }

    public void tryNewBallot() {
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
        return id;
    }

    @Override
    public void sendNextBallot(BallotNum b) {
        receiveNextBallot(new NextBallotMessage(b));
    }

    @Override
    public void sendLastVoteMessage(LastVoteMessage lvp) {
        receiveLastVote(lvp);
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
    public void handleRequest(Message request, Message response) {
        PaxosMessage pm = PaxosMessages.parseMessage(request.getData());
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
        BallotNum nextBal = ledger.getNextBallot();
        if (b.compareTo(nextBal) >= 0) {
            ledger.setNextBallot(b);
            nextBal = b;
        }
        BallotNum prevBal = ledger.getPrevBallot();
        if (nextBal.compareTo(prevBal) > 0) {
            LastVoteMessage lvp = new LastVoteMessage(id, prevBal, ledger.getPrevDec());
            int owner = b.processNum;
            PaxosParticipant participant = findParticipant(owner);
            participant.sendLastVoteMessage(lvp);
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
        BallotNum b = lv.vBal;
        BallotNum lastTried = ledger.getLastTried();
        if (b.equals(lastTried) && status == Status.TRYING) {
            prevVotes.add(new Vote(lv.p, lv.vBal, lv.vDec));
            if (prevVotes.size() >= 2) {
                startPolling();
            }
        }
    }

    void startPolling() {
        status = Status.POLLING;
        quorum = prevVotes.stream().map(v -> findParticipant(v.b.processNum)).collect(Collectors.toSet());
        voters.clear();
        Vote maxVote = prevVotes.stream().sorted((a,b) -> b.compareTo(a)).findFirst().get();
        Decree maxVoteDecree = maxVote.decree;
        if (maxVoteDecree.isNull())
            // Choose any decree
            decree = new Decree(0, 42);
        else
            decree = maxVoteDecree;

        beginBallot();
    }

    void beginBallot() {
        assert status == Status.POLLING;

        BallotNum b = ledger.getLastTried();
        for (PaxosParticipant p: quorum) {
            p.sendBeginBallot(b, decree);
        }
    }

    void receiveBeginBallot(BeginBallotMessage pm) {
        BallotNum b = pm.b;
        BallotNum nextBal = ledger.getNextBallot();
        if (b.equals(nextBal)) {
            BallotNum prevBal = ledger.getPrevBallot();
            if (b.compareTo(prevBal) > 0) {
                ledger.setPrevBallot(b);
                ledger.setPrevDec(pm.decree);
            }
            if (!prevBal.isNull()) {
                PaxosParticipant p = findParticipant(prevBal.processNum);
                p.sendVoted(prevBal, id);
            }
        }
    }

    void receiveVoted(VotedMessage vm) {
        BallotNum lastTried = ledger.getLastTried();
        BallotNum b = vm.prevBal;
        if (b.equals(lastTried) && status == Status.POLLING) {
            PaxosParticipant q = findParticipant(vm.owner);
            voters.add(q);
            if (voters.containsAll(quorum)) {
                Long v = ledger.getOutcome(decree.decreeNum);
                if (v == null) {
                    ledger.setOutcome(decree.decreeNum, decree.value);
                    for (PaxosParticipant p: all) {
                        p.sendSuccess(new Decree(decree.decreeNum, decree.value));
                    }
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
