package org.mengyun.tcctransaction.interceptor;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mengyun.tcctransaction.NoExistedTransactionException;
import org.mengyun.tcctransaction.SystemException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.utils.ReflectionUtils;
import org.mengyun.tcctransaction.utils.TransactionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by changmingxie on 10/30/15.
 */
public class CompensableTransactionInterceptor {

    static final Logger logger = Logger.getLogger(CompensableTransactionInterceptor.class.getSimpleName());

    private TransactionManager transactionManager;

    private Set<Class<? extends Exception>> delayCancelExceptions = new HashSet<Class<? extends Exception>>();

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayCancelExceptions) {
        this.delayCancelExceptions.addAll(delayCancelExceptions);
    }

    public Object interceptCompensableMethod(ProceedingJoinPoint pjp) throws Throwable {
        // compensableMethodContext包含transactionContext，从被拦截的方法入参中获取，
        // 通过.Compensable#transactionContextEditor（默认DefaultTransactionContextEditor）
        // 如果是事务发起方transactionContext就为null，事务参与方就取方法参数里的transactionContext
        //（从代码里看，也不一定非要transactionContext在方法参数第一位，只要有这个参数就行）
        CompensableMethodContext compensableMethodContext = new CompensableMethodContext(pjp);

        // 当前线程是否绑定事务Transaction，事务发起方调用为null，远程事务参与方一开始也为null
        boolean isTransactionActive = transactionManager.isTransactionActive();
        // 判断事务上下文是否合法
        if (!TransactionUtils.isLegalTransactionContext(isTransactionActive, compensableMethodContext)) {
            throw new SystemException("no active compensable transaction while propagation is mandatory for method " + compensableMethodContext.getMethod().getName());
        }

        // 计算方法类型
        switch (compensableMethodContext.getMethodRole(isTransactionActive)) {
            case ROOT:// 事务发起方走这里
                return rootMethodProceed(compensableMethodContext);
            case PROVIDER:// 远程事务参与方走这里
                return providerMethodProceed(compensableMethodContext);
            default:// 事务参与方走这里，会走到ResourceCoordinatorInterceptor
                return pjp.proceed();
        }
    }


    private Object rootMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {

        Object returnValue = null;

        Transaction transaction = null;
        // 是否开启异步confirm模式，这样可以提高性能
        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();
        // 是否开启异步cancel模式，这样可以提高性能
        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();

        Set<Class<? extends Exception>> allDelayCancelExceptions = new HashSet<Class<? extends Exception>>();
        allDelayCancelExceptions.addAll(this.delayCancelExceptions);
        allDelayCancelExceptions.addAll(Arrays.asList(compensableMethodContext.getAnnotation().delayCancelExceptions()));

        try {
            // 创建事务，并持久化，绑定事务到当前线程
            transaction = transactionManager.begin(compensableMethodContext.getUniqueIdentity());// 发起根事务

            try {
                // 这里直接走到ResourceCoordinatorInterceptor
                // 其实这里最后，就是事务发起方的try方法
                returnValue = compensableMethodContext.proceed();
            } catch (Throwable tryingException) {

                if (!isDelayCancelException(tryingException, allDelayCancelExceptions)) {// 是否延迟回滚（部分异常不适合立即回滚事务）

                    logger.warn(String.format("compensable transaction trying failed. transaction content:%s", JSON.toJSONString(transaction)), tryingException);
                    // 如果try失败，就执行参与者的cancel方法
                    // 这个时候参与者都初始化完毕（事务参与方是在事务发起方的try方法里执行，
                    // 走到这里就已经初始化里所有Participant，参考ResourceCoordinatorInterceptor）
                    transactionManager.rollback(asyncCancel);// 回滚事务
                }

                throw tryingException;
            }
            // try成功就执行参与者的confirm方法
            transactionManager.commit(asyncConfirm);// 提交事务

        } finally {
            // 清除线程中缓存的Transaction
            transactionManager.cleanAfterCompletion(transaction);
        }

        return returnValue;
    }

    private Object providerMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {

        Transaction transaction = null;

        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();

        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();

        try {
            // 远程事务参与方的transactionContext不为null，由事务发起方传过来
            switch (TransactionStatus.valueOf(compensableMethodContext.getTransactionContext().getStatus())) {
                case TRYING:// 事务发起方begin的时候，transaction状态为TRYING
                    // 通过传过来的TransactionContext，在服务提供者创建Transaction并保持Transaction.xid一致，代表一个全局事务
                    transaction = transactionManager.propagationNewBegin(compensableMethodContext.getTransactionContext());
                    // 调用远程事务参与方的try方法
                    return compensableMethodContext.proceed();
                case CONFIRMING:// 事务发起方commit的时候,transaction状态为CONFIRMING
                    /**
                     * 【注意】支持空回滚，但是不支持防悬挂
                     * 悬挂的意思是：在分支事务执行情况下，Cancel 比 Try 接口先执行，出现的原因是 Try 由于网络拥堵而超时，
                     * 事务管理器生成回滚，触发 Cancel 接口，而最终又收到了 Try 接口调用，但是 Cancel 比 Try 先到。
                     * 框架是允许空回滚的逻辑，如果分支事务没有（有可能try还没有开始执行，或者事务已经回滚过，事务删除了）
                     * 回滚会返回成功，事务管理器认为事务已回滚成功，整个事务回滚结束。而如果try方法最终到达，开始执行，分支事务创建，由于主事务已经回滚结束了，该分支事务不会因主事务回滚而被触发回滚了。这种情况下，定时恢复任务回扫描该分支事务，检查其主事务的状态，发现没有主事务，则回滚该分支事务。
                     */
                    try {
                        transaction = transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        // 调用远程事务参与方confirm方法
                        transactionManager.commit(asyncConfirm);
                    } catch (NoExistedTransactionException excepton) {// 吃掉NoExistedTransactionException看得出来是为了支持空回滚
                        //the transaction has been commit,ignore it.
                    }
                    break;
                case CANCELLING:// 事务发起方rollback的时候,transaction状态为CANCELLING
                    try {
                        transaction = transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        // 调用远程事务参与方的cancel方法
                        transactionManager.rollback(asyncCancel);
                    } catch (NoExistedTransactionException exception) {
                        //the transaction has been rollback,ignore it.
                    }
                    break;
            }

        } finally {
            transactionManager.cleanAfterCompletion(transaction);
        }

        Method method = compensableMethodContext.getMethod();

        return ReflectionUtils.getNullValue(method.getReturnType());
    }

    private boolean isDelayCancelException(Throwable throwable, Set<Class<? extends Exception>> delayCancelExceptions) {

        if (delayCancelExceptions != null) {
            for (Class delayCancelException : delayCancelExceptions) {

                Throwable rootCause = ExceptionUtils.getRootCause(throwable);

                if (delayCancelException.isAssignableFrom(throwable.getClass())
                        || (rootCause != null && delayCancelException.isAssignableFrom(rootCause.getClass()))) {
                    return true;
                }
            }
        }

        return false;
    }

}
