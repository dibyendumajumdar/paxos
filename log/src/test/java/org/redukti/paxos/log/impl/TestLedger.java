package org.redukti.paxos.log.impl;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestLedger {

    static final String BASE_PATH = "";
    static final int ID = 1;
    static final BallotNum NEG_INF = new BallotNum(-1, ID);
    static final Decree NULL_DECREE = new Decree(0, 0);

    @TempDir
    public Path temporaryFolder;

    @Test
    public void testOutOfSequenceOutcomes() throws Exception {

        File file = temporaryFolder.toFile();
        String basePath = file.getPath();
        try (Ledger ledger = LedgerImpl.createIfNotExisting(basePath, "l1", ID)) {
            Assertions.assertNotNull(ledger);
        }
        checkInvariantsForNewLedger(basePath);
        Decree d1 = new Decree(0, 101);
        Decree d2 = new Decree(1, 103);
        Decree d3 = new Decree(2, 105);
        Decree d4 = new Decree(3, 107);
        Decree d5 = new Decree(4, 109);

        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            Assertions.assertEquals(-1, ledger.getCommitNum());
            ledger.setOutcome(d1.decreeNum, d1.value);
            Assertions.assertEquals(d1.value, ledger.getOutcome(d1.decreeNum).longValue());
            Assertions.assertEquals(d1.decreeNum, ledger.getCommitNum());
            ledger.setOutcome(d3.decreeNum, d3.value);
            Assertions.assertEquals(d3.value, ledger.getOutcome(d3.decreeNum).longValue());
            Assertions.assertEquals(d1.decreeNum, ledger.getCommitNum());
            ledger.setOutcome(d4.decreeNum, d4.value);
            Assertions.assertEquals(d4.value, ledger.getOutcome(d4.decreeNum).longValue());
            Assertions.assertEquals(d1.decreeNum, ledger.getCommitNum());
            checkSize(new File(basePath, "l1"), 4);
        }

        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            checkSize(new File(basePath, "l1"), 4);
            Assertions.assertEquals(d1.value, ledger.getOutcome(d1.decreeNum).longValue());
            Assertions.assertEquals(d1.decreeNum, ledger.getCommitNum());
            Assertions.assertEquals(d3.value, ledger.getOutcome(d3.decreeNum).longValue());
            Assertions.assertEquals(d1.decreeNum, ledger.getCommitNum());
            Assertions.assertEquals(d4.value, ledger.getOutcome(d4.decreeNum).longValue());
            Assertions.assertEquals(d1.decreeNum, ledger.getCommitNum());
            ledger.setOutcome(d2.decreeNum, d2.value);
            Assertions.assertEquals(d2.value, ledger.getOutcome(d2.decreeNum).longValue());
            Assertions.assertEquals(d4.decreeNum, ledger.getCommitNum());
            ledger.setOutcome(d5.decreeNum, d5.value);
            Assertions.assertEquals(d5.value, ledger.getOutcome(d5.decreeNum).longValue());
            Assertions.assertEquals(d5.decreeNum, ledger.getCommitNum());
            checkSize(new File(basePath, "l1"), 5);
        }

    }

    private void checkInvariantsForNewLedger(String basePath) throws Exception {
        checkSize(new File(basePath,"l1"), 0);
        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            Assertions.assertEquals(NEG_INF, ledger.getNextBallot());
            Assertions.assertEquals(NEG_INF, ledger.getLastTried());
            Assertions.assertEquals(NEG_INF, ledger.getPrevBallot());
            Assertions.assertEquals(NULL_DECREE, ledger.getPrevDec());
            Assertions.assertEquals(-1, ledger.getCommitNum());
            Assertions.assertEquals(0, ledger.getUndecidedBallots().size());
        }
    }

    private void checkSize(File file, int outcomes) throws IOException {
        long expectedSize = LedgerImpl.Value.size()*outcomes + LedgerImpl.PAGE_SIZE;
        long actualSize = Files.size(Path.of(file.getPath()));
        Assertions.assertEquals(expectedSize, actualSize);
    }

    @Test
    public void testUndecidedBallots() throws Exception {

        File file = temporaryFolder.toFile();
        String basePath = file.getPath();
        try (Ledger ledger = LedgerImpl.createIfNotExisting(basePath, "l1", ID)) {
            Assertions.assertNotNull(ledger);
        }
        checkInvariantsForNewLedger(basePath);
        BallotNum b1 = new BallotNum(1, ID);
        Decree d1 = new Decree(0, 101);
        BallotNum b2 = new BallotNum(2, ID);
        Decree d2 = new Decree(1, 103);
        try (Ledger ledger = LedgerImpl.open(basePath, "l1", ID)) {
            ledger.setMaxVBal(b1, d1.decreeNum, d1.value);
            Assertions.assertEquals(1, ledger.getUndecidedBallots().size());
            Assertions.assertEquals(d1, ledger.getUndecidedBallots().get(0).decree);
            Assertions.assertEquals(b1, ledger.getUndecidedBallots().get(0).b);
            Assertions.assertEquals(b1, ledger.getMaxVBal());
            Assertions.assertEquals(d1, ledger.getMaxVal());
            ledger.setMaxVBal(b2, d2.decreeNum, d2.value);
            Assertions.assertEquals(2, ledger.getUndecidedBallots().size());
            Assertions.assertEquals(d1, ledger.getUndecidedBallots().get(0).decree);
            Assertions.assertEquals(b1, ledger.getUndecidedBallots().get(0).b);
            Assertions.assertEquals(b1, ledger.getMaxVBal());
            Assertions.assertEquals(d1, ledger.getMaxVal());
            Assertions.assertEquals(d2, ledger.getUndecidedBallots().get(1).decree);
            Assertions.assertEquals(b2, ledger.getUndecidedBallots().get(1).b);
            Assertions.assertEquals(-1, ledger.getCommitNum());

            ledger.setLastTried(b2);
            Assertions.assertEquals(b2, ledger.getLastTried());

            Assertions.assertEquals(2, ledger.getUndecidedBallots().size());
            Assertions.assertEquals(d1, ledger.getUndecidedBallots().get(0).decree);
            Assertions.assertEquals(b1, ledger.getUndecidedBallots().get(0).b);
            Assertions.assertEquals(b1, ledger.getMaxVBal());
            Assertions.assertEquals(d1, ledger.getMaxVal());
            Assertions.assertEquals(d2, ledger.getUndecidedBallots().get(1).decree);
            Assertions.assertEquals(b2, ledger.getUndecidedBallots().get(1).b);
            Assertions.assertEquals(-1, ledger.getCommitNum());

            ledger.setOutcome(d1.decreeNum, d1.value);
            Assertions.assertEquals(1, ledger.getUndecidedBallots().size());
            Assertions.assertEquals(d2, ledger.getUndecidedBallots().get(0).decree);
            Assertions.assertEquals(b2, ledger.getUndecidedBallots().get(0).b);
            Assertions.assertEquals(0, ledger.getCommitNum());
        }
        checkSize(new File(basePath,"l1"), 2);
    }
}
