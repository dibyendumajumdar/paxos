package org.redukti.paxos.basic;

import java.nio.ByteBuffer;

public class PaxosMessages {
    static final int NEXT_BALLOT_MESSAGE = 1;
    static final int LAST_VOTE_MESSAGE = 2;
    static final int BEGIN_BALLOT_MESSAGE = 3;
    static final int VOTED_MESSAGE = 4;
    static final int SUCCESS_MESSAGE = 5;

    public static PaxosMessage parseMessage(ByteBuffer bb) {
        int messageId = bb.getShort();
        switch (messageId) {
            case NEXT_BALLOT_MESSAGE: {
                return new NextBallotPaxosMessage(bb);
            }
            case LAST_VOTE_MESSAGE: {
                return new LastVotePaxosMessage(bb);
            }
            case BEGIN_BALLOT_MESSAGE: {
                return new BeginBallotMessage(bb);
            }
            case VOTED_MESSAGE: {
                return new VotedMessage(bb);
            }
            case SUCCESS_MESSAGE: {
                return new SuccessMessage(bb);
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

}