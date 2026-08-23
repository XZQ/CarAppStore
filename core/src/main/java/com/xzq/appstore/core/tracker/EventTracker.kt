package com.xzq.appstore.core.tracker

import com.xzq.appstore.core.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 业务事件打点基类。
 *
 * @param reporter 可选的事件上报通道，本地落盘后会把同一条事件转发给它；默认 NoOp
 */
open class EventTracker(private val reporter: EventReporter = NoOpEventReporter, protected val logger: AppLogger = AppLogger()) {
    /** 记录一次业务事件。 */
    open fun track(event: String) {
        runCatching { logger.d(TAG, event) }
    }

    /** 由子类调用，把已清洗的事件转发给上报通道。 */
    protected fun forward(event: TrackedEvent) {
        runCatching { reporter.report(listOf(event)) }
            .onSuccess { accepted ->
                if (!accepted) {
                    runCatching { logger.w(TAG, "reporter rejected event batch") }
                }
            }.onFailure { error ->
                runCatching { logger.w(TAG, "failed to report event: ${error.message}", error) }
            }
    }

    private companion object {
        const val TAG = "EventTracker"
    }
}

/**
 * 把事件以 `timestamp\tpayload` 形式追加落盘到本地文件，
 * 并把同一条事件转发给 [reporter] 用于外部上报。
 *
 * 落盘在专用后台作用域内串行执行：调用方通常处于主线程（页面曝光、按钮点击），
 * 不应在调用线程上同步打开/追加/关闭日志文件。
 */
class FileEventTracker(
    private val eventLogFile: File,
    reporter: EventReporter = NoOpEventReporter,
    private val clock: () -> Long = System::currentTimeMillis,
    logger: AppLogger = AppLogger(),
    /** 事件落盘协程作用域；默认单线程串行 IO，测试可注入即时作用域获得同步语义。 */
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
) : EventTracker(reporter, logger) {
    override fun track(event: String) {
        super.track(event)
        val safeEvent = event.replace('\n', ' ').replace('\r', ' ')
        val timestamp = clock()
        writeScope.launch {
            runCatching {
                eventLogFile.parentFile?.mkdirs()
                eventLogFile.appendText("$timestamp\t$safeEvent\n", Charsets.UTF_8)
            }.onFailure { error ->
                runCatching { logger.w(TAG, "failed to persist event: ${error.message}", error) }
            }
        }
        forward(TrackedEvent(timestamp, safeEvent))
    }

    private companion object {
        const val TAG = "FileEventTracker"
    }
}
