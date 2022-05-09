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
import org.redukti.paxos.log.api.Decree;

import java.util.Objects;

public abstract class PaxosParticipant {

    /**
     * Each PaxosParticipant has an id that marks its position in the
     * process array
     */
    public abstract int getId();

    // equivalent to phase 1 (a) prepare request
    public abstract void sendNextBallot(BallotNum b, int pid, long cnum);

    // equivalent to phase 1 (b) promise message
    public abstract void sendLastVoteMessage(BallotNum b, int pid, long cnum, Vote[] votes);

    // equivalent to phase 2 (a) accept request
    public abstract void sendBeginBallot(BallotNum b, int pid, long cnum, Decree[] chosenDecrees, Decree[] committedDecrees);

    public abstract void sendPendingVote(BallotNum b, int pid, long cnum);

    // equivalent to phase 2 (b) accepted message
    public abstract void sendVoted(BallotNum prevBal, int id);

    public abstract void sendSuccess(Decree[] decrees);

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaxosParticipant p = (PaxosParticipant) o;
        return getId() == p.getId();
    }
}
