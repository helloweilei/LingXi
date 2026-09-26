package com.lingxi.task;

public enum WritingTaskStatus {
    QUEUED,           // 已排队，待执行
    RUNNING,          // 执行中
    WAITING_APPROVAL, // 逐章确认：等待用户批准继续
    DONE,             // 全部完成
    FAILED,           // 失败（error 记录原因）
    CANCELLED         // 用户取消
}
