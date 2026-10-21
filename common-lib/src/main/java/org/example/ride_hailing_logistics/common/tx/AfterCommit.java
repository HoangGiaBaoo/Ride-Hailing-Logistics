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

    /**
     * Bu tru khi transaction cuc bo rollback sau khi da lam thay doi o service khac (saga don gian):
     * vd da giu tai xe o driver-service nhung luu offer that bai. Loi bu tru chi log; job doi soat lo phan con lai.
     */
    public static void onRollback(Runnable compensation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        compensation.run();
                    } catch (RuntimeException e) {
                        org.slf4j.LoggerFactory.getLogger(AfterCommit.class).warn("Compensation failed: {}", e.toString());
                    }
                }
            }
        });
    }
}
