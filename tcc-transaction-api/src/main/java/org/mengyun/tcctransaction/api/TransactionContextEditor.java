package org.mengyun.tcctransaction.api;

import java.lang.reflect.Method;

/**
 * Created by changming.xie on 1/18/17.
 */
public interface TransactionContextEditor {

    /**
     * 从参数中获得事务上下文
     *
     * @param target 对象
     * @param method 方法
     * @param args   参数
     * @return 事务上下文
     */
    public TransactionContext get(Object target, Method method, Object[] args);

    /**
     * 设置事务上下文到参数中
     *
     * @param transactionContext 事务上下文
     * @param target             对象
     * @param method             方法
     * @param args               参数
     */
    public void set(TransactionContext transactionContext, Object target, Method method, Object[] args);

}
