package org.mengyun.tcctransaction.spring.recover;

import org.mengyun.tcctransaction.OptimisticLockException;
import org.mengyun.tcctransaction.recover.RecoverConfig;

import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;

/**
 * 默认事务恢复配置实现
 *
 * Created by changming.xie on 6/1/16.
 */
public class DefaultRecoverConfig implements RecoverConfig {

    public static final RecoverConfig INSTANCE = new DefaultRecoverConfig();
    /**
     * 最大重试次数
     */
    private int maxRetryCount = 30;
    /**
     * 恢复间隔时间，单位：秒
     */
    private int recoverDuration = 120; //120 seconds
    /**
     * cron 表达式 (每分钟执行一次)
     */
    private String cronExpression = "0 */1 * * * ?";

    private int asyncTerminateThreadCorePoolSize = 512;

    private int asyncTerminateThreadMaxPoolSize = 1024;

    private int asyncTerminateThreadWorkQueueSize = 512;
    /**
     * 延迟取消异常集合
     */
    private Set<Class<? extends Exception>> delayCancelExceptions = new HashSet<Class<? extends Exception>>();

    /**
     * 在 DefaultRecoverConfig 构造方法里，预先添加了 OptimisticLockException / SocketTimeoutException 。
     *
     * 针对 SocketTimeoutException<br/>
     *      try 阶段，本地参与者调用远程参与者( 远程服务，例如 Dubbo，Http 服务)，远程
     * 参与者 try 阶段的方法逻辑执行时间较长，超过 Socket 等待时长，发生 SocketTimeoutException，如果立刻执行事务
     * 回滚，远程参与者 try 的方法未执行完成，可能导致 cancel 的方法实际未执行( try 的方法未执行完成，数据库事务【非 TCC 事务】
     * 未提交，cancel 的方法读取数据时发现未变更，导致方法实际未执行，最终 try 的方法执行完后，提交数据库事务【非 TCC 事务】，较为极端 )，
     * 最终引起数据不一致。在事务恢复时，会对这种情况的事务进行取消回滚，如果此时远程参与者的 try 的方法还未结束，还是可能发生数据不一致。
     *
     * 针对 OptimisticLockException<br/>
     *      还是 SocketTimeoutException 的情况，事务恢复间隔时间小于 Socket 超时时间，此时事务恢复调用远程参与者取消回滚事
     * 务，远程参与者下次更新事务时，会因为乐观锁更新失败，抛出 OptimisticLockException。如果 CompensableTransactionInterceptor
     * 此时立刻取消回滚，可能会和定时任务的取消回滚冲突，因此统一交给定时任务处理。
     */
    public DefaultRecoverConfig() {
        delayCancelExceptions.add(OptimisticLockException.class);
        delayCancelExceptions.add(SocketTimeoutException.class);
    }

    @Override
    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    @Override
    public int getRecoverDuration() {
        return recoverDuration;
    }

    @Override
    public String getCronExpression() {
        return cronExpression;
    }


    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public void setRecoverDuration(int recoverDuration) {
        this.recoverDuration = recoverDuration;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public void setAsyncTerminateThreadCorePoolSize(int asyncTerminateThreadCorePoolSize) {
        this.asyncTerminateThreadCorePoolSize = asyncTerminateThreadCorePoolSize;
    }

    public void setAsyncTerminateThreadMaxPoolSize(int asyncTerminateThreadMaxPoolSize) {
        this.asyncTerminateThreadMaxPoolSize = asyncTerminateThreadMaxPoolSize;
    }

    public void setAsyncTerminateThreadWorkQueueSize(int asyncTerminateThreadWorkQueueSize) {
        this.asyncTerminateThreadWorkQueueSize = asyncTerminateThreadWorkQueueSize;
    }

    @Override
    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayCancelExceptions) {
        this.delayCancelExceptions.addAll(delayCancelExceptions);
    }

    @Override
    public Set<Class<? extends Exception>> getDelayCancelExceptions() {
        return this.delayCancelExceptions;
    }

    @Override
    public int getAsyncTerminateThreadCorePoolSize() {
        return this.asyncTerminateThreadCorePoolSize;
    }

    @Override
    public int getAsyncTerminateThreadMaxPoolSize() {
        return this.asyncTerminateThreadMaxPoolSize;
    }

    @Override
    public int getAsyncTerminateThreadWorkQueueSize() {
        return this.asyncTerminateThreadWorkQueueSize;
    }

}
