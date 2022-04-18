package org.redukti.paxos.log.impl;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestLedger {

    static final String BASE_PATH = "ledger";
    static final int ID = 1;
    static final BallotNum NEG_INF = new BallotNum(-1, ID);
    static final Decree NULL_DECREE = new Decree(-1, 0);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testCreate() throws Exception {

        File file = temporaryFolder.newFolder(BASE_PATH);
        String basePath = file.getPath();
        try (Ledger ledger = LedgerImpl.createIfNotExisting(basePath, "l1", ID)) {
            Assert.assertNotNull(ledger);
        }
        checkSize(new File(basePath,"l1"), 1);
        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            Assert.assertEquals(NEG_INF, ledger.getNextBallot());
            Assert.assertEquals(NEG_INF, ledger.getLastTried());
            Assert.assertEquals(NEG_INF, ledger.getPrevBallot());
            Assert.assertEquals(NULL_DECREE, ledger.getPrevDec());
        }
        BallotNum firstBallot = new BallotNum(1, ID);
        Decree firstDecree = new Decree(firstBallot.proposalNumber, 101);
        BallotNum secondBallot = new BallotNum(2, ID);
        Decree secondDecree = new Decree(secondBallot.proposalNumber, 103);
        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            ledger.setNextBallot(secondBallot);
            ledger.setPrevBallot(firstBallot);
            ledger.setLastTried(secondBallot);
            ledger.setOutcome(firstDecree.decreeNum, firstDecree.value);
            ledger.setOutcome(secondDecree.decreeNum, secondDecree.value);
            ledger.setPrevDec(secondDecree);

            Assert.assertEquals(secondBallot, ledger.getNextBallot());
            Assert.assertEquals(firstBallot, ledger.getPrevBallot());
            Assert.assertEquals(secondBallot, ledger.getNextBallot());
            Assert.assertEquals(Long.valueOf(firstDecree.value), ledger.getOutcome(firstDecree.decreeNum));
            Assert.assertEquals(Long.valueOf(secondDecree.value), ledger.getOutcome(secondDecree.decreeNum));
            Assert.assertEquals(secondDecree, ledger.getPrevDec());
            Assert.assertNull(ledger.getOutcome(0));
            Assert.assertNull(ledger.getOutcome(3));
        }
        checkSize(new File(basePath,"l1"), 2);
        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            Assert.assertEquals(secondBallot, ledger.getNextBallot());
            Assert.assertEquals(firstBallot, ledger.getPrevBallot());
            Assert.assertEquals(secondBallot, ledger.getNextBallot());
            Assert.assertEquals(Long.valueOf(firstDecree.value), ledger.getOutcome(firstDecree.decreeNum));
            Assert.assertEquals(Long.valueOf(secondDecree.value), ledger.getOutcome(secondDecree.decreeNum));
            Assert.assertEquals(secondDecree, ledger.getPrevDec());
            Assert.assertNull(ledger.getOutcome(0));
            Assert.assertNull(ledger.getOutcome(3));
        }
        checkSize(new File(basePath,"l1"), 2);
    }

    private void checkSize(File file, int pages) throws IOException {
        long expectedSize = pages*LedgerImpl.PAGE_SIZE;
        long actualSize = Files.size(Path.of(file.getPath()));
        Assert.assertEquals(expectedSize, actualSize);
    }
}
