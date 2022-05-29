package org.redukti.paxos.log.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redukti.paxos.log.api.BallotNum;

public class TestBallotNum {

    @Test
    public void testBasics() {
        BallotNum null1 = new BallotNum(-1,0);
        BallotNum null2 = new BallotNum(-2,0);
        BallotNum null3 = new BallotNum(-1,1);
        Assertions.assertTrue(null1.isNull());
        Assertions.assertTrue(null2.isNull());
        Assertions.assertEquals(null1, null2);
        Assertions.assertTrue(null3.compareTo(null1) > 0);
        Assertions.assertTrue(null3.compareTo(null2) > 0);
        Assertions.assertEquals(null3, null3);

        BallotNum b1 = new BallotNum(1, 0);
        BallotNum b2 = new BallotNum(1, 0);
        BallotNum b3 = new BallotNum(2, 0);
        BallotNum b4 = new BallotNum(1, 1);

        Assertions.assertTrue(!b1.isNull());
        Assertions.assertTrue(!b2.isNull());
        Assertions.assertTrue(!b3.isNull());
        Assertions.assertTrue(!b4.isNull());
        Assertions.assertTrue(b1.compareTo(b2) == 0);
        Assertions.assertTrue(b1.compareTo(b3) < 0);
        Assertions.assertTrue(b3.compareTo(b4) > 0);
        Assertions.assertTrue(b4.compareTo(b2) > 0);
    }
}
