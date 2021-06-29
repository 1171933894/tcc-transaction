package org.mengyun.tcctransaction.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mengyun.tcctransaction.api.Compensable;
import org.mengyun.tcctransaction.api.Propagation;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.UniqueIdentity;
import org.mengyun.tcctransaction.common.MethodRole;
import org.mengyun.tcctransaction.support.FactoryBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Created by changming.xie on 04/04/19.
 */
public class CompensableMethodContext {

    ProceedingJoinPoint pjp = null;

    Method method = null;

    Compensable compensable = null;

    Propagation propagation = null;

    TransactionContext transactionContext = null;

    public CompensableMethodContext(ProceedingJoinPoint pjp) {
        this.pjp = pjp;
        this.method = getCompensableMethod();// 获得带 @Compensable 注解的方法
        this.compensable = method.getAnnotation(Compensable.class);
        this.propagation = compensable.propagation();
        // 获得 事务上下文
        this.transactionContext = FactoryBuilder.factoryOf(compensable.transactionContextEditor()).getInstance().get(pjp.getTarget(), method, pjp.getArgs());
    }

    public Compensable getAnnotation() {
        return compensable;
    }

    public Propagation getPropagation() {
        return propagation;
    }

    public TransactionContext getTransactionContext() {
        return transactionContext;
    }

    public Method getMethod() {
        return method;
    }

    public Object getUniqueIdentity() {
        Annotation[][] annotations = this.getMethod().getParameterAnnotations();

        for (int i = 0; i < annotations.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation.annotationType().equals(UniqueIdentity.class)) {

                    Object[] params = pjp.getArgs();
                    Object unqiueIdentity = params[i];

                    return unqiueIdentity;
                }
            }
        }

        return null;
    }

    /**
     * 获得带 @Compensable 注解的方法
     *
     * @return 方法
     */
    private Method getCompensableMethod() {
        Method method = ((MethodSignature) (pjp.getSignature())).getMethod();// 代理方法对象

        if (method.getAnnotation(Compensable.class) == null) {
            try {
                method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
        return method;
    }

    /**
     * 计算方法类型
     *
     * @param isTransactionActive 是否事务开启
     * @return 方法类型
     */
    public MethodRole getMethodRole(boolean isTransactionActive) {
        // Compensable.propagation默认是REQUIRED，也就是事务发起方为REQUIRED
        // 事务参与方配置了为SUPPORTS
        // 远程事务参与方没有配置默认REQUIRED
        if ((propagation.equals(Propagation.REQUIRED) && !isTransactionActive && transactionContext == null) ||// Propagation.REQUIRED：支持当前事务，当前没有事务，就新建一个事务。
                propagation.equals(Propagation.REQUIRES_NEW)) {// Propagation.REQUIRES_NEW：新建事务，如果当前存在事务，把当前事务挂起。
            /**
             * 方法类型为 MethodType.ROOT 时，发起根事务，判断条件如下二选一：
             *      事务传播级别为 Propagation.REQUIRED，并且当前没有事务。
             *      事务传播级别为 Propagation.REQUIRES_NEW，新建事务，如果当前存在事务，把当前事务挂起。此时，事务管理器的当前线程事务队列可能会存在多个事务。
             */
            return MethodRole.ROOT;// 事务发起方
        } else if ((propagation.equals(Propagation.REQUIRED) // Propagation.REQUIRED：支持当前事务
                || propagation.equals(Propagation.MANDATORY)) && !isTransactionActive && transactionContext != null) {// Propagation.MANDATORY：支持当前事务
            /**
             * 方法类型为 MethodType.ROOT 时，发起分支事务，判断条件如下二选一：
             *      事务传播级别为 Propagation.REQUIRED，并且当前不存在事务，并且方法参数传递了事务上下文。
             *      事务传播级别为 Propagation.PROVIDER，并且当前不存在事务，并且方法参数传递了事务上下文。
             *      当前不存在事务，方法参数传递了事务上下文是什么意思？当跨服务远程调用时，被调用服务本身( 服务提供者 )不在事务中，通过传递事务上下文参数，融入当前事务。
             */
            return MethodRole.PROVIDER;// 远程事务参与方（服务提供者）
        } else {
            /**
             * 方法类型为 MethodType.Normal 时，不进行事务处理
             */
            return MethodRole.NORMAL;// 事务参与方（也就是官方文档为什么要说配置成SUPPORTS）
        }
    }

    public Object proceed() throws Throwable {
        return this.pjp.proceed();
    }
}