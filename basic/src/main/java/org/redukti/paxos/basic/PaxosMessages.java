package org.redukti.paxos.basic;

import java.nio.ByteBuffer;

public class PaxosMessages {
    static final int NEXT_BALLOT_MESSAGE = 100;
    static final int LAST_VOTE_MESSAGE = 101;

    public static PaxosMessage parseMessage(ByteBuffer bb) {
        int messageId = bb.getShort();
        switch (messageId) {
            case NEXT_BALLOT_MESSAGE: {
                return new NextBallotPaxosMessage(bb);
            }
            case LAST_VOTE_MESSAGE: {
                return new LastVotePaxosMessage(bb);
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

}
