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
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class RemotePaxosParticipant extends PaxosParticipant implements ResponseHandler {

    final static Logger log = LoggerFactory.getLogger(RemotePaxosParticipant.class);

    int id;
    ProcessChannel remote;

    public RemotePaxosParticipant(int id, ProcessChannel remote) {
        this.id = id;
        this.remote = remote;
    }

    @Override
    public int getId() {
        return id;
    }

    PaxosMessage logit(PaxosMessage m) {
        log.info("Sending " + m + " to " + remote);
        return m;
    }

    @Override
    public void sendNextBallot(BallotNum b, long cnum) {
        remote.connection.submit(logit(new NextBallotMessage(b, id, cnum)).serialize(), this, Duration.ofSeconds(5));
    }

//    @Override
//    public void sendLastVoteMessage(BallotNum b, Vote v) {
//        remote.connection.submit(logit(new LastVoteMessage(b, v)).serialize(), this, Duration.ofSeconds(5));
//    }
//
//    @Override
//    public void sendBeginBallot(BallotNum b, Decree decree) {
//        remote.connection.submit(logit(new BeginBallotMessage(b, decree)).serialize(), this, Duration.ofSeconds(5));
//    }
//
//    @Override
//    public void sendVoted(BallotNum prevBal, int id) {
//        remote.connection.submit(logit(new VotedMessage(prevBal, id)).serialize(), this, Duration.ofSeconds(5));
//    }

    @Override
    public void sendSuccess(Decree[] decrees) {
        remote.connection.submit(logit(new SuccessMessage(decrees)).serialize(), this, Duration.ofSeconds(5));
    }

    @Override
    public void onTimeout() {

    }

    @Override
    public void onException(Exception e) {

    }

    @Override
    public void onResponse(Message response) {

    }
}
