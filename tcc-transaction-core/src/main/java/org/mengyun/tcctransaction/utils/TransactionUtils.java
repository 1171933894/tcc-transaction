package org.mengyun.tcctransaction.utils;

import org.mengyun.tcctransaction.api.Propagation;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.interceptor.CompensableMethodContext;

/**
 * Created by changming.xie on 2/23/17.
 */
public class TransactionUtils {

    /**
     * 判断事务上下文是否合法
     * 在 Propagation.MANDATORY 必须有在事务内
     *
     * @param isTransactionActive      是否
     * @param compensableMethodContext compensableMethodContext
     * @return 是否合法
     */
    public static boolean isLegalTransactionContext(boolean isTransactionActive, CompensableMethodContext compensableMethodContext) {

        // 当传播级别为 Propagation.MANDATORY 时，要求必须在事务中
        if (compensableMethodContext.getPropagation().equals(Propagation.MANDATORY) && !isTransactionActive && compensableMethodContext.getTransactionContext() == null) {
            return false;
        }

        return true;
    }
}
