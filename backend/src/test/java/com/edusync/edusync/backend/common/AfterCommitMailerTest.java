package com.edusync.edusync.backend.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests (no Spring, no database). A "transaction" is faked by
 * turning on TransactionSynchronizationManager by hand and then calling
 * afterCommit() / afterCompletion(ROLLED_BACK) ourselves, like Spring does.
 */
class AfterCommitMailerTest {

    private final AfterCommitMailer mailer = new AfterCommitMailer();

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        mailer.destroy();
    }

    @Test
    void insideTransaction_sendsOnlyAfterCommit_onAMailThread() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        TransactionSynchronizationManager.initSynchronization();
        mailer.sendAfterCommit("test email", () -> {
            threadName.set(Thread.currentThread().getName());
            sent.countDown();
        });

        // Not committed yet -> nothing sent
        assertFalse(sent.await(300, TimeUnit.MILLISECONDS), "sent before commit");

        commit();

        assertTrue(sent.await(5, TimeUnit.SECONDS), "not sent after commit");
        assertTrue(threadName.get().startsWith("mail-"), "sent on " + threadName.get());
    }

    @Test
    void rolledBack_neverSends() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);

        TransactionSynchronizationManager.initSynchronization();
        mailer.sendAfterCommit("test email", sent::countDown);

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertFalse(sent.await(500, TimeUnit.MILLISECONDS), "sent although the save rolled back");
    }

    @Test
    void commit_doesNotWaitForSlowEmails() {
        TransactionSynchronizationManager.initSynchronization();
        for (int i = 0; i < 5; i++) {
            mailer.sendAfterCommit("slow email " + i, () -> sleep(2000)); // like a slow Gmail call
        }

        long start = System.nanoTime();
        commit();
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // Sending them in the request thread would take 10 seconds
        assertTrue(ms < 500, "commit waited " + ms + " ms for the emails");
    }

    @Test
    void failingEmail_isOnlyLogged_andNextEmailStillSends() throws Exception {
        CountDownLatch secondSent = new CountDownLatch(1);

        assertDoesNotThrow(() -> {
            mailer.sendAfterCommit("broken email", () -> { throw new IllegalStateException("SMTP down"); });
            mailer.sendAfterCommit("good email", secondSent::countDown);
        });

        assertTrue(secondSent.await(5, TimeUnit.SECONDS), "a failed email stopped the next one");
    }

    @Test
    void outsideTransaction_sendsRightAway_inBackground() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        mailer.sendAfterCommit("test email", () -> {
            threadName.set(Thread.currentThread().getName());
            sent.countDown();
        });

        assertTrue(sent.await(5, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread().getName(), threadName.get());
    }

    // What Spring does after a successful commit
    private static void commit() {
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        syncs.forEach(TransactionSynchronization::afterCommit);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
