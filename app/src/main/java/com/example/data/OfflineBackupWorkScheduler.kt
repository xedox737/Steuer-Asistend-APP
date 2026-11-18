package com.example.data

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reconciles durable desired work without cancelling running or chaining failed jobs. */
internal class OfflineBackupWorkScheduler(context: Context, private val workerClass: Class<out ListenableWorker>,
    private val manager: WorkManager = WorkManager.getInstance(context), private val now: () -> Instant = Instant::now) {
    private val store = OfflineBackupScheduleStore(context)

    /** Safe to repeat after process death or a crash between schedule persistence and enqueue. */
    suspend fun reconcile() {
        try {
            var state = store.load() ?: return
            state.active?.let { active ->
                val observed = observe(name(active.generation, active.occurrence.id))
                store.reconcileStoppedClaim(active.occurrence.id, observed)
                state = store.load() ?: return
            }
            val next = state.next ?: return
            val workName = name(state.generation, next.id)
            val observed = observe(workName)
            if (!store.needsRegistration(state, observed)) return
            // A simultaneous edit may supersede this read. A stale request is still safe:
            // its generation/occurrence is checked again before a worker may claim it.
            val latest = store.load() ?: return
            if (latest.generation != state.generation || latest.next != next || !latest.config.enabled) return
            val current = now().toEpochMilli()
            require(current >= 0)
            val delay = if (next.dueMillis <= current) 0L else Math.subtractExact(next.dueMillis, current)
            val request = OneTimeWorkRequest.Builder(workerClass)
                .setInputData(Data.Builder().putString(KEY_GENERATION, state.generation).putString(KEY_OCCURRENCE, next.id).build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag(TAG).build()
            // KEEP deduplicates this occurrence only; no earlier running export is replaced.
            // https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work#conflict-resolution-policy
            manager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request).result.awaitResult()
        } catch (error: CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: ExecutionException) { invalid() }
        catch (_: ArithmeticException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }

    private suspend fun observe(workName: String): OfflineBackupScheduleStore.WorkState {
        val infos = manager.getWorkInfosForUniqueWork(workName).awaitResult()
        return when {
            infos.any { it.state == WorkInfo.State.RUNNING } -> OfflineBackupScheduleStore.WorkState.RUNNING
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> OfflineBackupScheduleStore.WorkState.WAITING
            infos.isEmpty() -> OfflineBackupScheduleStore.WorkState.MISSING
            else -> OfflineBackupScheduleStore.WorkState.FINISHED
        }
    }

    private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addListener({
            try { continuation.resume(get()) }
            catch (error: ExecutionException) { continuation.resumeWithException(error) }
            catch (error: java.util.concurrent.CancellationException) { continuation.cancel(error) }
            catch (error: Exception) { continuation.resumeWithException(error) }
        }, Executor { it.run() })
        continuation.invokeOnCancellation { cancel(false) }
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Der Sicherungszeitplan konnte nicht mit Android abgeglichen werden.")
    companion object {
        const val KEY_GENERATION = "schedule_generation"
        const val KEY_OCCURRENCE = "schedule_occurrence"
        const val TAG = "immopilot-local-backup"
        fun name(generation: String, occurrence: String) = "immopilot-backup-$generation-$occurrence"
    }
}
