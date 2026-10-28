package org.example.ride_hailing_logistics.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Chay tac dung phu ra ngoai DB (Redis read model, WebSocket) chi SAU KHI transaction commit:
 * neu rollback thi the gioi ben ngoai khong thay trang thai chua bao gio ton tai.
 * Hanh dong can ghi DB phai tu mo transaction REQUIRES_NEW: o pha afterCommit transaction cu van gan vao thread.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

}
