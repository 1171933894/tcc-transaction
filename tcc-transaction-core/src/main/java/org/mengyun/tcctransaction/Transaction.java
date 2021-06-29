package org.mengyun.tcctransaction;


import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.common.TransactionType;

import javax.transaction.xa.Xid;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by changmingxie on 10/26/15.
 */
public class Transaction implements Serializable {

    private static final long serialVersionUID = 7291423944314337931L;
    /**
     * 事务编号 用于唯一标识一个事务。使用 UUID 算法生成，保证唯一性
     *
     * note:<br/>
     * 参与者事务编号。通过 TransactionXid.globalTransactionId
     * 属性，关联上其所属的事务。当参与者进行远程调用时，远程的分支事务
     * 的事务编号等于该参与者的事务编号。通过事务编号的关联，TCC Confirm / Cancel 阶段，
     * 使用参与者的事务编号和远程的分支事务进行关联，从而实现事务的提交和回滚，
     */
    private TransactionXid xid;
    /**
     * 事务状态
     */
    private TransactionStatus status;
    /**
     * 事务类型
     */
    private TransactionType transactionType;
    /**
     * 重试次数 在 TCC 过程中，可能参与者异常崩溃，这个时候会进行重试直到成功或超过最大次数（用在事务恢复）
     */
    private volatile int retriedCount = 0;
    /**
     * 创建时间
     */
    private Date createTime = new Date();
    /**
     * 最后更新时间
     */
    private Date lastUpdateTime = new Date();
    /**
     * 版本号 用于乐观锁更新事务（用在事务存储器）
     */
    private long version = 1;
    /**
     * 参与者集合
     */
    private List<Participant> participants = new ArrayList<Participant>();
    /**
     * 附带属性映射
     */
    private Map<String, Object> attachments = new ConcurrentHashMap<String, Object>();

    public Transaction() {

    }

    /**
     * 创建分支事务
     *
     * @param transactionContext 事务上下文
     */
    public Transaction(TransactionContext transactionContext) {
        this.xid = transactionContext.getXid();// 事务上下文的 xid
        this.status = TransactionStatus.TRYING;// 尝试中状态
        this.transactionType = TransactionType.BRANCH;// 分支事务
    }

    /**
     * 创建指定类型的事务
     *
     * @param transactionType 事务类型
     */
    public Transaction(TransactionType transactionType) {
        this.xid = new TransactionXid();
        this.status = TransactionStatus.TRYING;// 尝试中状态
        this.transactionType = transactionType;
    }

    public Transaction(Object uniqueIdentity,TransactionType transactionType) {

        this.xid = new TransactionXid(uniqueIdentity);
        this.status = TransactionStatus.TRYING;
        this.transactionType = transactionType;
    }

    /**
     * 添加参与者
     *
     * @param participant 参与者
     */
    public void enlistParticipant(Participant participant) {
        participants.add(participant);
    }


    public Xid getXid() {
        return xid.clone();
    }

    public TransactionStatus getStatus() {
        return status;
    }


    public List<Participant> getParticipants() {
        return participants;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void changeStatus(TransactionStatus status) {
        this.status = status;
    }

    /**
     * 提交 TCC 事务
     */
    public void commit() {

        for (Participant participant : participants) {
            participant.commit();
        }
    }

    /**
     * 回滚 TCC 事务
     */
    public void rollback() {
        for (Participant participant : participants) {
            participant.rollback();
        }
    }

    public int getRetriedCount() {
        return retriedCount;
    }

    public void addRetriedCount() {
        this.retriedCount++;
    }

    public void resetRetriedCount(int retriedCount) {
        this.retriedCount = retriedCount;
    }

    public Map<String, Object> getAttachments() {
        return attachments;
    }

    public long getVersion() {
        return version;
    }

    public void updateVersion() {
        this.version++;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date date) {
        this.lastUpdateTime = date;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void updateTime() {
        this.lastUpdateTime = new Date();
    }


}
