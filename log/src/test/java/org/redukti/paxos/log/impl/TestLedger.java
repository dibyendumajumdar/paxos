package org.redukti.paxos.log.impl;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.BallotedDecree;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestLedger {

    static final String BASE_PATH = "ledger";
    static final int ID = 1;
    static final BallotNum NEG_INF = new BallotNum(-1, ID);
    static final Decree NULL_DECREE = new Decree(0, 0);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testOutOfSequenceOutcomes() throws Exception {

        File file = temporaryFolder.newFolder(BASE_PATH);
        String basePath = file.getPath();
        try (Ledger ledger = LedgerImpl.createIfNotExisting(basePath, "l1", ID)) {
            Assert.assertNotNull(ledger);
        }
        checkInvariantsForNewLedger(basePath);
        Decree d1 = new Decree(0, 101);
        Decree d2 = new Decree(1, 103);
        Decree d3 = new Decree(2, 105);
        Decree d4 = new Decree(3, 107);
        Decree d5 = new Decree(4, 109);

        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            Assert.assertEquals(-1, ledger.getCommitNum());
            ledger.setOutcome(d1.decreeNum, d1.value);
            Assert.assertEquals(d1.value, ledger.getOutcome(d1.decreeNum).longValue());
            Assert.assertEquals(d1.decreeNum, ledger.getCommitNum());
            ledger.setOutcome(d3.decreeNum, d3.value);
            Assert.assertEquals(d3.value, ledger.getOutcome(d3.decreeNum).longValue());
            Assert.assertEquals(d1.decreeNum, ledger.getCommitNum());
            ledger.setOutcome(d4.decreeNum, d4.value);
            Assert.assertEquals(d4.value, ledger.getOutcome(d4.decreeNum).longValue());
            Assert.assertEquals(d1.decreeNum, ledger.getCommitNum());
            checkSize(new File(basePath,"l1"), 4);
        }

        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            checkSize(new File(basePath,"l1"), 4);
            Assert.assertEquals(d1.value, ledger.getOutcome(d1.decreeNum).longValue());
            Assert.assertEquals(d1.decreeNum, ledger.getCommitNum());
            Assert.assertEquals(d3.value, ledger.getOutcome(d3.decreeNum).longValue());
            Assert.assertEquals(d1.decreeNum, ledger.getCommitNum());
            Assert.assertEquals(d4.value, ledger.getOutcome(d4.decreeNum).longValue());
            Assert.assertEquals(d1.decreeNum, ledger.getCommitNum());
            ledger.setOutcome(d2.decreeNum, d2.value);
            Assert.assertEquals(d2.value, ledger.getOutcome(d2.decreeNum).longValue());
            Assert.assertEquals(d4.decreeNum, ledger.getCommitNum());
            ledger.setOutcome(d5.decreeNum, d5.value);
            Assert.assertEquals(d5.value, ledger.getOutcome(d5.decreeNum).longValue());
            Assert.assertEquals(d5.decreeNum, ledger.getCommitNum());
            checkSize(new File(basePath,"l1"), 5);
        }

    }

    private void checkInvariantsForNewLedger(String basePath) throws Exception {
        checkSize(new File(basePath,"l1"), 0);
        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            Assert.assertEquals(NEG_INF, ledger.getNextBallot());
            Assert.assertEquals(NEG_INF, ledger.getLastTried());
            Assert.assertEquals(NEG_INF, ledger.getPrevBallot());
            Assert.assertEquals(NULL_DECREE, ledger.getPrevDec());
            Assert.assertEquals(-1, ledger.getCommitNum());
            Assert.assertEquals(0, ledger.getUndecidedBallots().size());
        }
    }

    private void checkSize(File file, int outcomes) throws IOException {
        long expectedSize = LedgerImpl.Value.size()*outcomes + LedgerImpl.PAGE_SIZE;
        long actualSize = Files.size(Path.of(file.getPath()));
        Assert.assertEquals(expectedSize, actualSize);
    }

    @Test
    public void testUndecidedBallots() throws Exception {

        File file = temporaryFolder.newFolder(BASE_PATH);
        String basePath = file.getPath();
        try (Ledger ledger = LedgerImpl.createIfNotExisting(basePath, "l1", ID)) {
            Assert.assertNotNull(ledger);
        }
        checkInvariantsForNewLedger(basePath);
        BallotNum b1 = new BallotNum(1, ID);
        Decree d1 = new Decree(0, 101);
        BallotNum b2 = new BallotNum(2, ID);
        Decree d2 = new Decree(1, 103);
        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            ledger.setMaxVBal(b1, d1.decreeNum, d1.value);
            Assert.assertEquals(1, ledger.getUndecidedBallots().size());
            Assert.assertEquals(d1, ledger.getUndecidedBallots().get(0).decree);
            Assert.assertEquals(b1, ledger.getUndecidedBallots().get(0).b);
            Assert.assertEquals(b1, ledger.getMaxVBal());
            Assert.assertEquals(d1, ledger.getMaxVal());
            ledger.setMaxVBal(b2, d2.decreeNum, d2.value);
            Assert.assertEquals(2, ledger.getUndecidedBallots().size());
            Assert.assertEquals(d1, ledger.getUndecidedBallots().get(0).decree);
            Assert.assertEquals(b1, ledger.getUndecidedBallots().get(0).b);
            Assert.assertEquals(b1, ledger.getMaxVBal());
            Assert.assertEquals(d1, ledger.getMaxVal());
            Assert.assertEquals(d2, ledger.getUndecidedBallots().get(1).decree);
            Assert.assertEquals(b2, ledger.getUndecidedBallots().get(1).b);
            Assert.assertEquals(-1, ledger.getCommitNum());

            ledger.setLastTried(b2);
            Assert.assertEquals(b2, ledger.getLastTried());

            Assert.assertEquals(2, ledger.getUndecidedBallots().size());
            Assert.assertEquals(d1, ledger.getUndecidedBallots().get(0).decree);
            Assert.assertEquals(b1, ledger.getUndecidedBallots().get(0).b);
            Assert.assertEquals(b1, ledger.getMaxVBal());
            Assert.assertEquals(d1, ledger.getMaxVal());
            Assert.assertEquals(d2, ledger.getUndecidedBallots().get(1).decree);
            Assert.assertEquals(b2, ledger.getUndecidedBallots().get(1).b);
            Assert.assertEquals(-1, ledger.getCommitNum());

            ledger.setOutcome(d1.decreeNum, d1.value);
            Assert.assertEquals(1, ledger.getUndecidedBallots().size());
            Assert.assertEquals(d2, ledger.getUndecidedBallots().get(1).decree);
            Assert.assertEquals(b2, ledger.getUndecidedBallots().get(1).b);
            Assert.assertEquals(0, ledger.getCommitNum());
        }
        checkSize(new File(basePath,"l1"), 2);
    }
}
