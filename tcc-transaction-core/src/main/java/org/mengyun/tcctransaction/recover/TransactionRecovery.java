package org.mengyun.tcctransaction.recover;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.OptimisticLockException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionRepository;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.common.TransactionType;
import org.mengyun.tcctransaction.support.TransactionConfigurator;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by changmingxie on 11/10/15.
 *
 * Q：如果应用集群部署，会不会相同事务被多个定时任务同时重试？
 * A：答案是不会，事务在重试时会乐观锁更新，同时只有一个应用节点能更新成功。
 */
public class TransactionRecovery {

    static final Logger logger = Logger.getLogger(TransactionRecovery.class.getSimpleName());

    private TransactionConfigurator transactionConfigurator;
    /**
     * 启动恢复事务逻辑
     */
    public void startRecover() {
        // 加载异常事务集合
        /**
         * 异常事务的定义：当前时间超过 - 事务变更时间( 最后执行时间 ) >= 事务恢复间隔( RecoverConfig#getRecoverDuration() )。
         * 这里有一点要注意，已完成的事务会从事务存储器删除。
         */
        List<Transaction> transactions = loadErrorTransactions();
        // 恢复异常事务集合
        recoverErrorTransactions(transactions);
    }

    private List<Transaction> loadErrorTransactions() {


        long currentTimeInMillis = Calendar.getInstance().getTimeInMillis();

        TransactionRepository transactionRepository = transactionConfigurator.getTransactionRepository();
        RecoverConfig recoverConfig = transactionConfigurator.getRecoverConfig();

        return transactionRepository.findAllUnmodifiedSince(new Date(currentTimeInMillis - recoverConfig.getRecoverDuration() * 1000));
    }

    private void recoverErrorTransactions(List<Transaction> transactions) {


        for (Transaction transaction : transactions) {
            // 超过最大重试次数
            if (transaction.getRetriedCount() > transactionConfigurator.getRecoverConfig().getMaxRetryCount()) {
                // 当单个事务超过最大重试次数时，不再重试，只打印异常，此时需要人工介入解决。可以接入 ELK 收集日志监控报警
                logger.error(String.format("recover failed with max retry count,will not try again. txid:%s, status:%s,retried count:%d,transaction content:%s", transaction.getXid(), transaction.getStatus().getId(), transaction.getRetriedCount(), JSON.toJSONString(transaction)));
                continue;
            }
            // 分支事务超过最大可重试时间
            if (transaction.getTransactionType().equals(TransactionType.BRANCH)
                    && (transaction.getCreateTime().getTime() +
                    transactionConfigurator.getRecoverConfig().getMaxRetryCount() *
                            transactionConfigurator.getRecoverConfig().getRecoverDuration() * 1000
                    > System.currentTimeMillis())) {
                continue;
            }
            // Confirm / Cancel
            try {
                // 增加重试次数
                transaction.addRetriedCount();
                // Confirm
                if (transaction.getStatus().equals(TransactionStatus.CONFIRMING)) {

                    transaction.changeStatus(TransactionStatus.CONFIRMING);
                    transactionConfigurator.getTransactionRepository().update(transaction);
                    transaction.commit();
                    transactionConfigurator.getTransactionRepository().delete(transaction);
                // Cancel
                } else if (transaction.getStatus().equals(TransactionStatus.CANCELLING)
                        || transaction.getTransactionType().equals(TransactionType.ROOT)) {

                    transaction.changeStatus(TransactionStatus.CANCELLING);
                    transactionConfigurator.getTransactionRepository().update(transaction);
                    transaction.rollback();
                    transactionConfigurator.getTransactionRepository().delete(transaction);
                }

            } catch (Throwable throwable) {

                if (throwable instanceof OptimisticLockException
                        || ExceptionUtils.getRootCause(throwable) instanceof OptimisticLockException) {
                    logger.warn(String.format("optimisticLockException happened while recover. txid:%s, status:%s,retried count:%d,transaction content:%s", transaction.getXid(), transaction.getStatus().getId(), transaction.getRetriedCount(), JSON.toJSONString(transaction)), throwable);
                } else {
                    logger.error(String.format("recover failed, txid:%s, status:%s,retried count:%d,transaction content:%s", transaction.getXid(), transaction.getStatus().getId(), transaction.getRetriedCount(), JSON.toJSONString(transaction)), throwable);
                }
            }
        }
    }

    public void setTransactionConfigurator(TransactionConfigurator transactionConfigurator) {
        this.transactionConfigurator = transactionConfigurator;
    }
}
