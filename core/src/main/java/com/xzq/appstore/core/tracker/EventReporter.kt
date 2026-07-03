package com.xzq.appstore.core.tracker

/**
 * 单条已记录的业务事件，供上报层消费。
 *
 * @param timestamp 事件发生的毫秒时间戳
 * @param payload 已清洗的事件文本，已去除换行符
 */
data class TrackedEvent(
    val timestamp: Long,
    val payload: String,
)

/**
 * 事件上报接缝：把本地落盘的事件上传到外部平台（埋点 / 监控 / 数据看板）。
 *
 * 仓库内只定义协议和默认 NoOp 实现；真实上传服务由生产环境通过实现注入。
 * 实现可自行决定批量、重试、退避和上报线程策略。
 */
interface EventReporter {
    /**
     * 上报一批事件。
     *
     * @return true 表示至少成功入队 / 已交给上传通道；false 表示本次上报失败，调用方可保留事件用于下次重试
     */
    fun report(events: List<TrackedEvent>): Boolean
}

/**
 * 默认无操作上报器：未接入外部平台时使用，所有上报视为成功。
 */
object NoOpEventReporter : EventReporter {
    override fun report(events: List<TrackedEvent>): Boolean = true
}
