package com.edusync.edusync.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends emails in the background, and only after the database save is committed.
 *
 * Why: sending one email through Gmail takes about 1-2 seconds. Before this, an
 * Excel import of 200 users kept the admin's request open for minutes. Now the
 * request returns at once and 2 "mail-" threads send the emails one by one.
 *
 * Rules:
 *  - Inside a transaction: nothing is sent if it rolls back; sending starts after commit.
 *  - Outside a transaction: sending starts right away (still in the background).
 *  - A failed email is only logged; the saved data stays. The caller never sees the error.
 *  - At most 1000 emails wait in the queue; more than that are dropped and logged.
 *  - On shutdown, emails already queued get up to 30 seconds to finish.
 *
 * The pool lives inside this class on purpose: a pool registered as a Spring bean
 * would replace Spring Boot's default "applicationTaskExecutor".
 */
@Component
public class AfterCommitMailer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitMailer.class);

    private final ThreadPoolTaskExecutor executor;

    public AfterCommitMailer() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
    }

    /**
     * @param description who the email is for, used only in log messages (e.g. "welcome email to a@b.c")
     * @param send        the code that builds and sends the email
     */
    public void sendAfterCommit(String description, Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(description, send);
                }
            });
        } else {
            submit(description, send);
        }
    }

    private void submit(String description, Runnable send) {
        try {
            executor.execute(() -> {
                try {
                    send.run();
                } catch (Exception e) {
                    log.warn("Could not send the {}: {}", description, e.getMessage());
                }
            });
        } catch (TaskRejectedException e) {
            // Queue full (or shutting down). Never throw here: the data is already committed.
            log.warn("Email queue is full, dropped the {}", description);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
