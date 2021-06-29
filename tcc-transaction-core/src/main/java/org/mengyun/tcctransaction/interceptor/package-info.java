package org.mengyun.tcctransaction.interceptor;

/**
 * 主要通过spring-aop拦截器CompensableTransactionInterceptor、ResourceCoordinatorInterceptor
 *
 * CompensableTransactionInterceptor : 用于tcc事务的流程执行begin（try)、commit(confirm)、rollback(cancel)
 *
 * ResourceCoordinatorInterceptor : 用于记录tcc事务的Participant（参与方）
 */