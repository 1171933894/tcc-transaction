package org.mengyun.tcctransaction.dubbo.context;

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.RpcContext;
import com.alibaba.fastjson.JSON;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionContextEditor;
import org.mengyun.tcctransaction.dubbo.constants.TransactionContextConstants;

import java.lang.reflect.Method;

/**
 * Dubbo 事务上下文编辑器实现，通过 Dubbo 隐式传参方式获得事务上下文
 *
 *      通过 Dubbo 的隐式传参的方式，避免在 Dubbo Service 接口上声明 TransactionContext 参数，对接口产生一定的入侵
 *
 * Created by changming.xie on 1/19/17.
 */
public class DubboTransactionContextEditor implements TransactionContextEditor {
    @Override
    public TransactionContext get(Object target, Method method, Object[] args) {

        String context = RpcContext.getContext().getAttachment(TransactionContextConstants.TRANSACTION_CONTEXT);

        if (StringUtils.isNotEmpty(context)) {
            return JSON.parseObject(context, TransactionContext.class);
        }

        return null;
    }

    @Override
    public void set(TransactionContext transactionContext, Object target, Method method, Object[] args) {

        RpcContext.getContext().setAttachment(TransactionContextConstants.TRANSACTION_CONTEXT, JSON.toJSONString(transactionContext));
    }
}
