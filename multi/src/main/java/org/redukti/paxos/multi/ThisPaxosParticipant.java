/**
 * MIT License
 * <p>
 * Copyright (c) 2022 Dibyendu Majumdar
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.redukti.paxos.multi;

import org.redukti.logging.Logger;
import org.redukti.logging.LoggerFactory;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.BallotedDecree;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.redukti.paxos.net.api.RequestResponseSender;

import java.util.*;

/**
 * ThisPaxosParticipant implements Multi Paxos.
 * ThisPaxosParticipant performs all the various roles, proposer, acceptor, learner.
 * The implementation was started following closely the description of the algorithm in
 * Leslie Lamport's Part Time Parliament paper, p12, p25-27.
 * Some terms have been amended based on later work by Leslie, in particular the TLA+
 * specification of basic paxos.
 */
public class ThisPaxosParticipant extends PaxosParticipant implements RequestHandler {

    static final Logger log = LoggerFactory.DEFAULT.getLogger(ThisPaxosParticipant.class.getName());

    /**
     * Each Paxos process has its unique id.
     */
    final int pid;
    /**
     * Ledger is where the process persists the data it needs
     */
    final Ledger ledger;

    /**
     * Status of the process, initial IDLE. See PTP p25.
     */
    volatile Status status = Status.IDLE;
    /**
     * If status == POLLING, then the set of quorum members from whom
     * we have received Voted messages in the current ballot; otherwise, meaningless.
     * See PTP p25. (phase 2 voters)
     */
    Set<PaxosParticipant> voters = new LinkedHashSet<>();
    /**
     * The previous votes sent by participants in LastVoteMessages,
     * by decree number. (phase 1 voters)
     */
    TreeMap<Long, Set<Vote>> prevVotes = new TreeMap<>();
    /**
     * phase 1 responders, and their commit nums, map from pid to commitNum
     */
    Map<Integer, Long> prevVoters = new LinkedHashMap<>();
    /**
     * Chosen values for all decrees in ballot, decree number to value mapping.
     * Only valid when status == POLLING
     */
    TreeMap<Long, Long> chosenValues = new TreeMap<>();
    /**
     * The dnum assigned to the client request
     */
    long chosenDNum = -1; // meaningful only if there is a client request
    /**
     * All participants including ThisPaxosParticipant.
     */
    Set<PaxosParticipant> all = new LinkedHashSet<>();

    /**
     * Current client request
     */
    ClientRequestMessage currentRequest;
    RequestResponseSender currentResponseSender;

    List<ClientRequestHolder> clientQueue = new ArrayList<>();


    public ThisPaxosParticipant(int id, Ledger ledger) {
        this.ledger = ledger;
        this.pid = id;
        all.add(this);
    }

    /**
     * Add remote participants ensuring we have an odd number in total
     */
    public synchronized void addRemotes(List<? extends PaxosParticipant> remoteParticipants) {
        Objects.requireNonNull(remoteParticipants);
        if ((remoteParticipants.size() + 1) % 2 == 0 || remoteParticipants.isEmpty())
            throw new IllegalArgumentException("Number of participants must be odd and greater than 1");
        all.addAll(remoteParticipants);
    }

    /**
     * Find a participant given its pid; we should only be communicating with registered
     * participants, hence not finding the participant is impossible.
     */
    synchronized PaxosParticipant findParticipant(int pid) {
        for (PaxosParticipant p : all) {
            if (p.getId() == pid)
                return p;
        }
        throw new IllegalArgumentException("Participant " + pid + " is not known");
    }

    @Override
    public int getId() {
        return pid;
    }

    /**
     * Add a client request to the queue
     *
     * @param responseSender       This is the callback to invoke to send a message back to the client
     * @param clientRequestMessage This is the client request
     */
    synchronized void receiveClientRequest(RequestResponseSender responseSender, ClientRequestMessage clientRequestMessage) {
        log.info(getClass(), "receiveClientRequest", "Received " + clientRequestMessage);
        clientQueue.add(new ClientRequestHolder(clientRequestMessage, responseSender));
    }

    /**
     * Start processing a client request
     * Called from synchronized method so thread-safe
     */
    synchronized void processClientRequest(RequestResponseSender responseSender, ClientRequestMessage clientRequestMessage) {
        log.info(getClass(), "processClientRequest", "Processing " + clientRequestMessage);
        this.currentRequest = clientRequestMessage;
        this.currentResponseSender = responseSender;
        chosenDNum = -1;
        chosenValues.clear();
        voters.clear();
        if (status == Status.IDLE) {
            // try to become the leader
            tryNewBallot();
        } else if (status == Status.POLLING && ledger.getLastTried().equals(ledger.getMaxBal())) {
            // already the leader so we can skip phase 1
            startPolling();
        }
    }

    /**
     * Entrypoint to initiate processing a client request.
     */
    public synchronized void doOneClientRequest() {
        if (currentRequest != null)
            return;
        if (clientQueue.isEmpty())
            return;
        ClientRequestHolder clientRequestHolder = clientQueue.remove(0);
        if (status == Status.IDLE || (status == Status.POLLING && ledger.getLastTried().equals(ledger.getMaxBal()))) {
            processClientRequest(clientRequestHolder.responseSender, clientRequestHolder.request);
        } else {
            // Send error response to client
            sendClientResponse(clientRequestHolder.responseSender, -1, 0);
        }
    }

    public synchronized boolean isPendingClientRequests() {
        return !clientQueue.isEmpty();
    }

    public synchronized boolean isHandlingClientRequest() {
        return currentRequest != null;
    }

    /**
     * Start a new ballot (i.e. leader election)
     * Also known as Phase1a(b)
     * In the Phase1a(b) action, it sends to all acceptors a phase 1a message that begins ballot b.
     * See PTP p26. In theory always enabled but here we enable it when the process is IDLE.
     */
    public synchronized void tryNewBallot() {
        assert status == Status.IDLE;

        // Set lastTried[p] to any ballot number b, greater than its previous
        // value, such that owner(b) = p.
        BallotNum b = ledger.getLastTried(); // highest ballot tried so far
        BallotNum maxBal = ledger.getMaxBal(); // highest ballot seen so far
        b = new BallotNum(Math.max(b.proposalNumber, maxBal.proposalNumber) + 1, getId());
        ledger.setLastTried(b);
        status = Status.TRYING;
        prevVotes.clear();
        prevVoters.clear();
        nextBallot(b, ledger.getCommitNum());
    }

    /**
     * Sends a NextBallot message to all participants including myself
     *
     * @param b         ballot number
     * @param commitNum My commitNum
     */
    synchronized void nextBallot(BallotNum b, long commitNum) {
        for (PaxosParticipant p : all) {
            p.sendNextBallot(b, pid, commitNum);
        }
    }

    @Override
    public synchronized void sendNextBallot(BallotNum b, int pid, long commitNum) {
        receiveNextBallot(new NextBallotMessage(b, pid, commitNum));
    }

    /**
     * Retrieve all decrees that are in my ledger with committed status
     * but the sender of the message is missing
     *
     * @param pi Participant who wants to get an update of commits
     */
    synchronized Decree[] getCommittedDecrees(ParticipantInfo pi) {
        if (pi.commitNum() < ledger.getCommitNum()) {
            ArrayList<Decree> decrees = new ArrayList<>();
            for (long cnum = pi.commitNum() + 1; cnum <= ledger.getCommitNum(); cnum++) {
                Long outcome = ledger.getOutcome(cnum);
                if (outcome != null) {
                    decrees.add(new Decree(cnum, outcome));
                }
            }
            if (!decrees.isEmpty()) {
                return decrees.toArray(new Decree[decrees.size()]);
            }
        }
        return new Decree[0];
    }

    /**
     * If the participant has commitnum < ledger.commitnum then
     * send an update to it, with all the commits that we know about but
     * they don't
     */
    synchronized void updateParticipant(ParticipantInfo pm) {
        if (pm.getPid() == getId())
            return;
        Decree[] committedDecrees = getCommittedDecrees(pm);
        if (committedDecrees.length > 0) {
            PaxosParticipant p = findParticipant(pm.getPid());
            p.sendSuccess(committedDecrees);
        }
    }

    /**
     * Retrieve any votes cast in undecided (pending) ballots.
     */
    static synchronized Vote[] getVotes(int pid, Ledger ledger) {
        List<BallotedDecree> undecidedBallots = ledger.getUndecidedBallots();
        Vote[] votes = new Vote[undecidedBallots.size()];
        for (int i = 0; i < undecidedBallots.size(); i++) {
            votes[i] = new Vote(pid, undecidedBallots.get(i).b, undecidedBallots.get(i).decree);
        }
        return votes;
    }

    @Override
    public void sendNack(BallotNum b, BallotNum maxBal, int pid) {
        receiveNack(new NackMessage(b, maxBal, pid));
    }

    private void logMessageReceived(ParticipantInfo m, String method) {
        log.info(getClass(), method, "Received by " + getId() + " from " + m.getPid() + " " + m);
    }

    /**
     * Process a NextBallot message (phase 1a) - if it is greater than any ballots
     * we have seen then send a LastVote message to the ballot sender (also our status must be set to
     * idle)
     * <p>
     * If We are on a ballot that is > than the sender then send a Nack.
     */
    synchronized void receiveNextBallot(NextBallotMessage pm) {
        logMessageReceived(pm, "receiveNextBallot");
        updateParticipant(pm);
        BallotNum b = pm.b;
        BallotNum maxBal = ledger.getMaxBal();
        if (b.compareTo(maxBal) > 0) {
            ledger.setMaxBal(b);
            if (b.owner() != getId() && status != Status.IDLE) {
                // We got a ballot from another process
                resetToIdle();
            }
            int owner = b.processNum; // process that sent us NextBallotMessage
            PaxosParticipant p = findParticipant(owner);
            // v is the vote with the largest ballot number
            // that we have cast, or its null if we haven't yet
            // voted in a ballot.
            Vote[] votes = getVotes(getId(), ledger);
            p.sendLastVoteMessage(b, getId(), ledger.getCommitNum(), votes);
        } else if (b.compareTo(maxBal) < 0) {
            // The proposer is behind, so let it know that we have seen a later ballot number
            int owner = b.processNum; // process that sent us NextBallotMessage
            PaxosParticipant p = findParticipant(owner);
            p.sendNack(b, maxBal, getId());
        }
    }


    @Override
    public synchronized void sendLastVoteMessage(BallotNum b, int pid, long cnum, Vote[] votes) {
        receiveLastVote(new LastVoteMessage(b, pid, cnum, votes));
    }

    synchronized int quorumSize() {
        return (all.size() + 1) / 2;
    }

    /**
     * Process a LastVote message. If the sender is behind with commits then send
     * updates to it. If the ballot is ours and we got quorum then commence phase 2.
     */
    synchronized void receiveLastVote(LastVoteMessage lv) {
        logMessageReceived(lv, "receiveLastVote");
        updateParticipant(lv);
        BallotNum b = lv.b;
        BallotNum lastTried = ledger.getLastTried();
        if (b.equals(lastTried) && status == Status.TRYING) {
            for (int i = 0; i < lv.votes.length; i++) {
                Vote v = lv.votes[i];
                prevVotes.computeIfAbsent(v.decree.decreeNum, k -> new LinkedHashSet<>()).add(v);
            }
            prevVoters.put(lv.pid, lv.cnum);
            if (prevVoters.size() >= quorumSize()) {
                startPolling();
            }
        }
    }

    /**
     * Look at all dnums > commitNum upto and including the max dnum for which we got a
     * vote. If we have any gaps in the votes then assign NO-OP value to those.
     * The new value gets the dnum > that all committed / voted values.
     * The idea of assigning NO-OP values to gaps comes from Lamport's PTP paper.
     */
    synchronized void determineChosenValues() {
        chosenDNum = -1;
        long maxDnumInVotes = -1;
        if (!prevVotes.isEmpty()) {
            maxDnumInVotes = prevVotes.lastKey();
            for (long dnum = ledger.getCommitNum() + 1; dnum <= maxDnumInVotes; dnum++) {
                Set<Vote> votes = prevVotes.get(dnum);
                Vote maxVote = null;
                if (votes != null) {
                    maxVote = votes.stream().max(Comparator.naturalOrder()).orElse(null);
                }
                Long value;
                if (maxVote == null || maxVote.ballotNum.isNull()) {
                    value = Decree.NOOP_VAL; // assign NO-OP val to the gap decree
                } else {
                    assert (dnum == maxVote.decree.decreeNum);
                    value = maxVote.decree.value;
                }
                chosenValues.put(dnum, value);
            }
        }
        chosenDNum = Math.max(ledger.getCommitNum() + 1, maxDnumInVotes + 1);
        chosenValues.put(chosenDNum, currentRequest.requestedValue);
    }

    /**
     * Start phase 2 - i.e. we are now the leader as we got
     * a quorum of participants promising to ignore all ballot < ours.
     */
    synchronized void startPolling() {
        status = Status.POLLING;
        determineChosenValues();
        beginBallot();
    }

    /**
     * Part of Phase2a(b,v) - send a BeginBallot message to participants
     * including myself
     */
    synchronized void beginBallot() {
        assert status == Status.POLLING;
        BallotNum b = ledger.getLastTried();
        for (PaxosParticipant p : acceptors()) {
            p.sendBeginBallot(b, getId(), ledger.getCommitNum(), getChosenDecrees(), new Decree[0]);
        }
    }

    /**
     * Format the chosenValues as decrees
     */
    synchronized Decree[] getChosenDecrees() {
        Decree[] decrees = new Decree[chosenValues.size()];
        int i = 0;
        for (Map.Entry<Long, Long> e : chosenValues.entrySet()) {
            decrees[i++] = new Decree(e.getKey(), e.getValue());
        }
        return decrees;
    }

    Set<PaxosParticipant> acceptors() {
        return all;
    }

    @Override
    public synchronized void sendBeginBallot(BallotNum b, int pid, long cnum, Decree[] chosenDecrees, Decree[] committedDecrees) {
        assert b.processNum == pid;
        receiveBeginBallot(new BeginBallotMessage(b, pid, cnum, chosenDecrees, committedDecrees));
    }

    /**
     * Also known as Phase2b(a), receive BeginBallot from the leader/proposer.
     * Unlike the PTP paper where only processes that participated in phase 1 responded,
     * here, like in the 2019 TLA+ spec of basic paxos, we respond if the ballot is greater than
     * or equal to any we have seen. Note that therefore we must update our maxBal.
     *
     * We also have an enhancement here - if we see that the proposer is ahead of us in
     * commits (this can happen if we had crashed and rejoined after a period of time and therefore
     * didn't see some commits) - we send a PendingVote message - this tells proposer that we are
     * ready to wait, but we need any commits we don't know about. The proposer will resend the
     * BeginBallot with the missing commits.
     *
     * If we know about all the commits then we are good to respond with Voted message.
     *
     * If the proposer's ballot number is less than what we know, then we send a Nack message.
     */
    synchronized void receiveBeginBallot(BeginBallotMessage pm) {
        logMessageReceived(pm, "receiveBeginBallot");
        BallotNum b = pm.b;
        BallotNum maxBal = ledger.getMaxBal();
        if (b.compareTo(maxBal) >= 0) {
            ledger.setMaxBal(b);
            if (b.owner() != getId() && status != Status.IDLE) {
                // We got a ballot from another process
                resetToIdle();
            }
            for (int i = 0; i < pm.committedDecrees.length; i++) {
                ledger.setOutcome(pm.committedDecrees[i].decreeNum, pm.committedDecrees[i].value);
            }
            for (int i = 0; i < pm.chosenDecrees.length; i++) {
                ledger.setMaxVBal(b, pm.chosenDecrees[i].decreeNum, pm.chosenDecrees[i].value);
            }
            PaxosParticipant p = findParticipant(b.processNum);
            if (ledger.getCommitNum() < pm.cnum) {
                p.sendPendingVote(b, getId(), ledger.getCommitNum());
            } else {
                p.sendVoted(b, pid);
            }
        } else {
            // The proposer is behind, so let it know that we have seen a later ballot number
            int owner = pm.pid; // process that sent us BeginBallotMessage
            PaxosParticipant p = findParticipant(owner);
            p.sendNack(b, maxBal, getId());
        }
    }

    @Override
    public synchronized void sendPendingVote(BallotNum b, int pid, long cnum) {
        receivePendingVote(new PendingVoteMessage(b, pid, cnum));
    }

    /**
     * Process a PendingVote message - this means that the sender is behind in terms of
     * commits. We resend them the BeginBallot message again but this time include any commits
     * they need to know about.
     */
    synchronized void receivePendingVote(PendingVoteMessage m) {
        logMessageReceived(m, "receivePendingVote");
        BallotNum lastTried = ledger.getLastTried();
        BallotNum b = m.b;
        PaxosParticipant p = findParticipant(m.pid);
        if (b.equals(lastTried) && status == Status.POLLING) {
            p.sendBeginBallot(m.b, getId(), ledger.getCommitNum(), getChosenDecrees(), getCommittedDecrees(m));
        } else {
            // Okay we are not leading anymore but still inform the sender about commits they are missing.
            updateParticipant(m);
        }
    }

    @Override
    public synchronized void sendVoted(BallotNum prevBal, int id) {
        receiveVoted(new VotedMessage(prevBal, id));
    }

    synchronized void receiveVoted(VotedMessage vm) {
        log.info(getClass(), "receiveVoted", "Received by " + getId() + " from " + vm.pid + " " + vm);
        BallotNum lastTried = ledger.getLastTried();
        BallotNum b = vm.b;
        if (b.equals(lastTried) && status == Status.POLLING) {
            PaxosParticipant q = findParticipant(vm.pid);
            voters.add(q);
            if (haveQuorumOfVoters()) {
                for (Map.Entry<Long, Long> e : chosenValues.entrySet()) {
                    Long v = ledger.getOutcome(e.getKey());
                    if (v == null) {
                        ledger.setOutcome(e.getKey(), e.getValue());
                    }
                }
                Decree[] chosenDecrees = getChosenDecrees();
                for (PaxosParticipant p : all) {
                    p.sendSuccess(chosenDecrees);
                }
                sendClientResponse(chosenDecrees);
            }
        }
    }

    synchronized boolean haveQuorumOfVoters() {
        return voters.size() == quorumSize();
    }

    @Override
    public synchronized void sendSuccess(Decree[] decrees) {
        receiveSuccess(new SuccessMessage(decrees));
    }

    synchronized void receiveSuccess(SuccessMessage sm) {
        log.info(getClass(), "receiveSuccess", "Received " + sm);
        for (int i = 0; i < sm.decree.length; i++) {
            Decree d = sm.decree[i];
            Long v = ledger.getOutcome(d.decreeNum);
            if (v == null) {
                ledger.setOutcome(d.decreeNum, d.value);
            }
        }
    }

    synchronized void sendClientResponse(Decree[] chosenDecrees) {
        Long chosenValue = null;
        for (Decree d : chosenDecrees) {
            if (chosenDNum >= 0 && d.decreeNum == chosenDNum)
                chosenValue = d.value;
        }
        ClientRequestMessage crm = currentRequest;
        if (crm != null && chosenValue != null) {
            sendClientResponse(currentResponseSender, chosenDNum, chosenValue);
        }
        currentResponseSender = null;
        currentRequest = null;
        prevVotes.clear();
        voters.clear();
    }

    synchronized void sendClientResponse(RequestResponseSender sender, long dnum, long value) {
        ClientResponseMessage rm = new ClientResponseMessage(dnum, value);
        sender.setData(rm.serialize());
        sender.submit();
    }

    synchronized void receiveNack(NackMessage pm) {
        log.info(getClass(), "receiveNack", "Received " + pm);
        if (status != Status.IDLE && pm.b.equals(ledger.getLastTried()) && pm.maxBal.compareTo(ledger.getMaxBal()) > 0) {
            ledger.setMaxBal(pm.maxBal);
            resetToIdle();
        }
    }

    synchronized void resetToIdle() {
        status = Status.IDLE;
        prevVotes.clear();
        prevVoters.clear();
        chosenValues.clear();
        voters.clear();
        chosenDNum = -1;
        if (currentRequest != null) {
            // inform client, dnum = -1 indicates error
            sendClientResponse(currentResponseSender, -1, 0);
        }
        currentRequest = null;
        currentResponseSender = null;
    }

    @Override
    public synchronized void handleRequest(Message request, RequestResponseSender responseSender) {
        PaxosMessage pm = PaxosMessages.parseMessage(request.getCorrelationId(), request.getData());
        if (pm instanceof NextBallotMessage) {
            receiveNextBallot((NextBallotMessage) pm);
        } else if (pm instanceof LastVoteMessage) {
            receiveLastVote((LastVoteMessage) pm);
        } else if (pm instanceof BeginBallotMessage) {
            receiveBeginBallot((BeginBallotMessage) pm);
        } else if (pm instanceof PendingVoteMessage) {
            receivePendingVote((PendingVoteMessage) pm);
        } else if (pm instanceof VotedMessage) {
            receiveVoted((VotedMessage) pm);
        } else if (pm instanceof SuccessMessage) {
            receiveSuccess((SuccessMessage) pm);
        } else if (pm instanceof NackMessage) {
            receiveNack((NackMessage) pm);
        } else if (pm instanceof ClientRequestMessage) {
            receiveClientRequest(responseSender, (ClientRequestMessage) pm);
        } else {
            log.error(getClass(), "handleRequest", "Unknown message " + pm);
        }
    }

    static final class ClientRequestHolder {
        final ClientRequestMessage request;
        final RequestResponseSender responseSender;

        public ClientRequestHolder(ClientRequestMessage request, RequestResponseSender responseSender) {
            this.request = request;
            this.responseSender = responseSender;
        }
    }

}
