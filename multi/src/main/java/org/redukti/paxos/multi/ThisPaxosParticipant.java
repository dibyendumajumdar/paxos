/**
 * MIT License
 *
 * Copyright (c) 2022 Dibyendu Majumdar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.redukti.paxos.multi;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.BallotedDecree;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.redukti.paxos.net.api.RequestResponseSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ThisPaxosParticipant is essentially the implementation of Basic Paxos.
 * This is where the paxos algorithm is implemented.
 * ThisPaxosParticipant performs all the various roles, proposer, acceptor, learner.
 * The implementation was started following closely the description of the algorithm in
 * Leslie Lamport's Part Time Parliament paper, p12, p25-27.
 * Some terms have been amended based on later work by Leslie, in particular the TLA+
 * specification of basic paxos.
 */
public class ThisPaxosParticipant extends PaxosParticipant implements RequestHandler {

    final static Logger log = LoggerFactory.getLogger(ThisPaxosParticipant.class);

    /**
     * Each Paxos process has its unique id.
     */
    final int myId;
    final Ledger ledger;

    /**
     * Status of the process, initial IDLE. See PTP p25.
     */
    volatile Status status = Status.IDLE;
    /**
     * If status == POLLING, then the set of quorum members from whom
     * we have received Voted messages in the current ballot; otherwise, meaningless.
     * See PTP p25.
     */
    Set<PaxosParticipant> voters = new LinkedHashSet<>();
    /**
     * The previous votes sent by participants in LastVoteMessages,
     * by decree number.
     */
    TreeMap<Long, Set<Vote>> prevVotes = new TreeMap<>();
    /**
     * phase 1 responders, and their commit nums
     */
    Map<Integer, Long> prevVoters = new LinkedHashMap<>();
    /**
     * Chosen values for all decrees in ballot, decree number to value mapping
     */
    TreeMap<Long, Long> chosenValues = new TreeMap<>();
    /**
     * All participants including ThisPaxosParticipant.
     */
    Set<PaxosParticipant> all = new LinkedHashSet<>();

    volatile ClientRequestMessage currentRequest;
    volatile RequestResponseSender currentResponseSender;

    public ThisPaxosParticipant(int id, Ledger ledger) {
        this.ledger = ledger;
        this.myId = id;
        all.add(this);
    }

    public synchronized void addRemotes(List<? extends PaxosParticipant> remoteParticipants) {
        Objects.requireNonNull(remoteParticipants);
        if ((remoteParticipants.size()+1)%2 == 0 || remoteParticipants.size() == 0)
            throw new IllegalArgumentException("Number of participants must be odd and greater than 1");
        all.addAll(remoteParticipants);
    }

    PaxosParticipant findParticipant(int owner) {
        for (PaxosParticipant p: all) {
            if (p.getId() == owner)
                return p;
        }
        throw new IllegalArgumentException();
    }

    @Override
    public int getId() {
        return myId;
    }

    /**
     * Start processing a client request
     * Called from synchronized method so thread-safe
     */
    void receiveClientRequest(RequestResponseSender responseSender, ClientRequestMessage pm) {
        log.info("Received " + pm);
        if (currentRequest == null) {
            this.currentRequest = pm;
            this.currentResponseSender = responseSender;
            tryNewBallot();
        }
        else {
            // queue it
        }
    }

    /**
     * Also known as Phase1a(b)
     * In the Phase1a(b) action, it sends to all acceptors a phase 1a message that begins ballot b.
     * See PTP p26. In theory always enabled but here we enable it when the process is IDLE.
     */
    public synchronized void tryNewBallot() {
        if (status != Status.IDLE) {
            return;
        }
        // Set lastTried[p] to any ballot number b, greater than its previous
        // value, such that owner(b) = p.
        BallotNum b = ledger.getLastTried();
        b = b.increment();
        assert b.owner() == getId();
        ledger.setLastTried(b);
        status = Status.TRYING;
        prevVotes.clear();
        prevVoters.clear();
        nextBallot(b, ledger.getCommitNum());
    }

    void nextBallot(BallotNum b, long cnum) {
        for (PaxosParticipant p: all) {
            p.sendNextBallot(b, cnum);
        }
    }

    @Override
    public void sendNextBallot(BallotNum b, long cnum) {
        receiveNextBallot(new NextBallotMessage(b,myId,cnum));
    }

    Decree[] getCommittedDecrees(ParticipantInfo pi) {
        if (pi.commitNum() < ledger.getCommitNum()) {
            ArrayList<Decree> decrees = new ArrayList<>();
            for (long cnum = pi.commitNum()+1; cnum < ledger.getCommitNum(); cnum++) {
                Long outcome = ledger.getOutcome(cnum);
                if (outcome != null) {
                    decrees.add(new Decree(cnum, outcome));
                }
            }
            if (decrees.size() > 0) {
                return decrees.toArray(new Decree[decrees.size()]);
            }
        }
        return new Decree[0];
    }

    /**
     * If the next ballot sender has commitnum < ledger.commitnum then
     * send an update
     */
    void updateParticipant(ParticipantInfo pm) {
        if (pm.getPid() == getId())
            return;
        Decree[] committedDecrees = getCommittedDecrees(pm);
        if (committedDecrees.length > 0) {
            PaxosParticipant p = findParticipant(pm.getPid());
            p.sendSuccess(committedDecrees);
        }
    }

    Vote[] getVotes() {
        List<BallotedDecree> undecidedBallots = ledger.getUndecidedBallots();
        Vote[] votes = new Vote[undecidedBallots.size()];
        for (int i = 0; i < undecidedBallots.size(); i++) {
            votes[i] = new Vote(getId(), undecidedBallots.get(i).b, undecidedBallots.get(i).decree);
        }
        return votes;
    }

    void receiveNextBallot(NextBallotMessage pm) {
        log.info("Received " + pm);
        updateParticipant(pm);
        BallotNum b = pm.b;
        BallotNum maxBal = ledger.getMaxBal();
        if (receiveNextBallotEnabled(b, maxBal)) {
            int owner = b.processNum; // process that sent us NextBallotMessage
            PaxosParticipant p = findParticipant(owner);
            // v is the vote with the largest ballot number
            // that we have cast, or its null if we haven't yet
            // voted in a ballot.
            Vote[] votes = getVotes();
            p.sendLastVoteMessage(b, getId(), ledger.getCommitNum(), votes);
        }
    }

    boolean receiveNextBallotEnabled(BallotNum b, BallotNum maxBal) {
        if (b.compareTo(maxBal) > 0) {
            ledger.setMaxBal(b);
            return true;
        }
        return false;
    }

    @Override
    public void sendLastVoteMessage(BallotNum b, int pid, long cnum, Vote[] votes) {
        receiveLastVote(new LastVoteMessage(b, pid, cnum, votes));
    }

    int quorumSize() {
        return (all.size()+1)/2;
    }

    void receiveLastVote(LastVoteMessage lv) {
        log.info("Received " + lv);
        updateParticipant(lv);
        BallotNum b = lv.b;
        BallotNum lastTried = ledger.getLastTried();
        if (b.equals(lastTried) && status == Status.TRYING) {
            for (int i = 0; i < lv.votes.length; i++) {
                Vote v = lv.votes[i];
                prevVotes.computeIfAbsent(v.decree.decreeNum, (k) -> new LinkedHashSet<>()).add(v);
            }
            prevVoters.put(lv.pid, lv.cnum);
            if (prevVoters.size() >= quorumSize()) {
                startPolling();
            }
        }
    }

    void determineChosenValues() {
        long newdnum = -1;
        for (long dnum: prevVotes.keySet()) {
            Set<Vote> votes = prevVotes.get(dnum);
            Vote maxVote = votes.stream().max(Comparator.naturalOrder()).get();
            Long value;
            if (maxVote == null || maxVote.ballotNum.isNull()) {
                if (newdnum < 0) {
                    newdnum = dnum;
                }
                value = currentRequest.requestedValue;
            }
            else {
                assert(dnum == maxVote.decree.decreeNum);
                value = maxVote.decree.value;
            }
            chosenValues.put(dnum, value);
        }
        if (newdnum < 0) {
            newdnum = ledger.getCommitNum()+1;
            chosenValues.put(newdnum, currentRequest.requestedValue);
        }
    }

//     Part of Phase2a(b,v)
    void startPolling() {
        status = Status.POLLING;
        determineChosenValues();
        beginBallot();
    }
//
//    void abort() {
//        status = Status.IDLE;
//        quorum.clear();
//        voters.clear();
//    }
//
    /**
     * Part of Phase2a(b,v)
     */
    void beginBallot() {
        assert status == Status.POLLING;
        BallotNum b = ledger.getLastTried();
        for (PaxosParticipant p: acceptors()) {
            p.sendBeginBallot(b, getId(), ledger.getCommitNum(), getChosenDecrees(), new Decree[0]);
        }
    }

    Decree[] getChosenDecrees() {
        Decree[] decrees = new Decree[chosenValues.size()];
        int i = 0;
        for (Map.Entry<Long,Long> e: chosenValues.entrySet()) {
            decrees[i++] = new Decree(e.getKey(), e.getValue());
        }
        return decrees;
    }

    Set<PaxosParticipant> acceptors() {
        return all;
    }

    @Override
    public void sendBeginBallot(BallotNum b, int pid, long cnum, Decree[] chosenDecrees, Decree[] committedDecrees) {
        receiveBeginBallot(new BeginBallotMessage(b, pid, cnum, chosenDecrees, committedDecrees));
    }

    /**
     * Also known as Phase2b(a)
     */
    void receiveBeginBallot(BeginBallotMessage pm) {
        log.info("Received " + pm);
        BallotNum b = pm.b;
        BallotNum maxBal = ledger.getMaxBal();
        if (receiveBeginBallotEnabled(b, maxBal)) {
            for (int i = 0; i < pm.committedDecrees.length; i++) {
                ledger.setOutcome(pm.committedDecrees[i].decreeNum, pm.committedDecrees[i].value);
            }
            for (int i = 0; i < pm.chosenDecrees.length; i++) {
                ledger.setMaxVBal(b, pm.chosenDecrees[i].decreeNum, pm.chosenDecrees[i].value);
            }
            PaxosParticipant p = findParticipant(b.processNum);
            if (ledger.getCommitNum() < pm.cnum) {
                p.sendPendingVote(b, getId(), ledger.getCommitNum());
            }
            else {
                p.sendVoted(b, myId);
            }
        }
    }

    boolean receiveBeginBallotEnabled(BallotNum b, BallotNum maxBal) {
        if (b.compareTo(maxBal) >= 0) {
            ledger.setMaxBal(b);
            return true;
        }
        return false;
    }

    @Override
    public void sendPendingVote(BallotNum b, int pid, long cnum) {
        receivePendingVote(new PendingVoteMessage(b, pid, cnum));
    }

    void receivePendingVote(PendingVoteMessage m) {
        log.info("Received " + m);
        BallotNum lastTried = ledger.getLastTried();
        BallotNum b = m.b;
        if (b.equals(lastTried) && status == Status.POLLING) {
            PaxosParticipant p = findParticipant(m.pid);
            p.sendBeginBallot(m.b, getId(), ledger.getCommitNum(), getChosenDecrees(), getCommittedDecrees(m));
        }
    }

    @Override
    public void sendVoted(BallotNum prevBal, int id) {
        receiveVoted(new VotedMessage(prevBal, id));
    }

    void receiveVoted(VotedMessage vm) {
        log.info("Received " + vm);
        BallotNum lastTried = ledger.getLastTried();
        BallotNum b = vm.b;
        if (b.equals(lastTried) && status == Status.POLLING) {
            PaxosParticipant q = findParticipant(vm.pid);
            voters.add(q);
            if (haveQuorumOfVoters()) {
                for (Map.Entry<Long,Long> e: chosenValues.entrySet()) {
                    Long v = ledger.getOutcome(e.getKey());
                    if (v == null) {
                        ledger.setOutcome(e.getKey(), e.getValue());
                    }
                }
                for (PaxosParticipant p: all) {
                    p.sendSuccess(getChosenDecrees());
                }
            }
        }
    }

    boolean haveQuorumOfVoters() {
        return voters.size() == quorumSize();
    }

    @Override
    public void sendSuccess(Decree[] decrees) {
        receiveSuccess(new SuccessMessage(decrees));
    }

    void receiveSuccess(SuccessMessage sm) {
        log.info("Received " + sm);
        for (int i = 0; i < sm.decree.length; i++) {
            Decree d = sm.decree[i];
            Long v = ledger.getOutcome(d.decreeNum);
            if (v == null) {
                ledger.setOutcome(d.decreeNum, d.value);
            }
        }
        // FIXME - outcome need to be matched to the assigned dnum
//        ClientRequestMessage crm = currentRequest;
//        if (crm != null) {
//            ClientResponseMessage rm = new ClientResponseMessage(v);
//            currentResponseSender.setData(rm.serialize());
//            currentResponseSender.submit();
//            currentResponseSender = null;
//            currentRequest = null;
//        }
        status = Status.IDLE;
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
        else if (pm instanceof PendingVoteMessage) {
            receivePendingVote((PendingVoteMessage) pm);
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

}
