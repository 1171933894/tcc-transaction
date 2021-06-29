package org.mengyun.tcctransaction.recover;

import java.util.Set;

/**
 * 事务信息被持久化到外部的存储器中。事务存储是事务恢复的基础。通过读取外部存储器中的异常事务，
 * 定时任务会按照一定频率对事务进行重试，直到事务完成或超过最大重试次数。
 *
 * Created by changming.xie on 6/1/16.
 */
public interface RecoverConfig {
    /**
     * @return 最大重试次数
     */
    public int getMaxRetryCount();
    /**
     * @return 恢复间隔时间，单位：秒
     */
    public int getRecoverDuration();
    /**
     * @return cron 表达式
     */
    public String getCronExpression();
    /**
     * @return 延迟取消异常集合
     */
    public Set<Class<? extends Exception>> getDelayCancelExceptions();
    /**
     * 设置延迟取消异常集合
     *
     * @param delayRecoverExceptions 延迟取消异常集合
     */
    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayRecoverExceptions);

    public int getAsyncTerminateThreadCorePoolSize();

    public int getAsyncTerminateThreadMaxPoolSize();

    public int getAsyncTerminateThreadWorkQueueSize();
}
