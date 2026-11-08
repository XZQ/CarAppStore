package com.xzq.appstore.core.tracker

import com.xzq.appstore.core.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 业务事件打点基类。
 *
 * @param reporter 可选的事件上报通道，本地落盘后会把同一条事件转发给它；默认 NoOp
 */
open class EventTracker(private val reporter: EventReporter = NoOpEventReporter, protected val logger: AppLogger = AppLogger()) {
    /** 上报通道尚未接收的事件，按发生顺序保留，下一次上报时一并重试。 */
    private val pendingEvents = ArrayDeque<TrackedEvent>()
    private val pendingLock = Any()

    /** 记录一次业务事件。 */
    open fun track(event: String) {
        runCatching { logger.d(TAG, event) }
    }

    /**
     * 由子类调用，把已清洗的事件转发给上报通道。
     *
     * 上报通道返回 false 或抛异常时事件不会丢弃：它留在内存队列里，随下一条事件一起重试，
     * 直到被接收。队列有上限，长时间离线时丢弃最旧的事件，避免无界内存增长。
     */
    protected fun forward(event: TrackedEvent) {
        val batch = synchronized(pendingLock) {
            pendingEvents.addLast(event)
            while (pendingEvents.size > MAX_PENDING_EVENTS) {
                pendingEvents.removeFirst()
            }
            pendingEvents.toList()
        }
        var reported = false
        val accepted = try {
            reporter.report(batch).also { reported = true }
        } catch (error: Exception) {
            runCatching { logger.w(TAG, "failed to report event: ${error.message}", error) }
            false
        }
        if (accepted) {
            // 只移除本次成功上报的前 batch.size 条，期间新追加的事件继续保留。
            synchronized(pendingLock) {
                repeat(batch.size) { pendingEvents.removeFirstOrNull() }
            }
        } else if (reported) {
            // 只有通道明确返回 false 才记「拒绝」；抛异常已在上面记录过原因，避免重复告警。
            runCatching { logger.w(TAG, "reporter rejected ${batch.size} pending event(s); kept for retry") }
        }
    }

    /** 当前等待重试的事件数量，仅用于诊断与测试。 */
    internal fun pendingEventCount(): Int = synchronized(pendingLock) { pendingEvents.size }

    private companion object {
        const val TAG = "EventTracker"

        /** 待重试事件上限；超出后丢弃最旧事件。 */
        const val MAX_PENDING_EVENTS = 200
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
    /** 单个事件文件的大小上限，超过后在下一次写入前轮转；测试可注入小值验证轮转。 */
    private val maxLogBytes: Long = DEFAULT_MAX_LOG_BYTES,
) : EventTracker(reporter, logger) {
    override fun track(event: String) {
        super.track(event)
        val safeEvent = event.replace('\n', ' ').replace('\r', ' ')
        val timestamp = clock()
        writeScope.launch {
            runCatching {
                rotateIfNeeded()
                eventLogFile.parentFile?.mkdirs()
                eventLogFile.appendText("$timestamp\t$safeEvent\n", Charsets.UTF_8)
            }.onFailure { error ->
                runCatching { logger.w(TAG, "failed to persist event: ${error.message}", error) }
            }
        }
        forward(TrackedEvent(timestamp, safeEvent))
    }

    /**
     * 文件超过 [maxLogBytes] 时轮转：旧一代（.1）删除，当前文件改名为 .1，新事件写回空文件。
     * 在单线程写作用域内执行，无并发竞争；保留一代足够满足本地分析用途。
     */
    private fun rotateIfNeeded() {
        if (!eventLogFile.exists() || eventLogFile.length() <= maxLogBytes) {
            return
        }
        val rotated = File(eventLogFile.parentFile, eventLogFile.name + ROTATED_FILE_SUFFIX)
        Files.move(eventLogFile.toPath(), rotated.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val TAG = "FileEventTracker"

        /** 事件文件默认上限 1MB。 */
        const val DEFAULT_MAX_LOG_BYTES = 1024L * 1024L

        /** 轮转保留一代的后缀。 */
        const val ROTATED_FILE_SUFFIX = ".1"
    }
}
