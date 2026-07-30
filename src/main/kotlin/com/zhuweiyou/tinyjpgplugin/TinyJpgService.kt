package com.zhuweiyou.tinyjpgplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Semaphore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

object TinyJpgService {

    private val LOG = Logger.getInstance(TinyJpgService::class.java)

    private const val MAX_FILE_SIZE = 5L * 1024 * 1024   // 5MB
    private const val MAX_CONCURRENCY = 2
    private val SUPPORTED_EXT = setOf("jpg", "jpeg", "png", "webp")
    private const val UPLOAD_URL = "https://tinyjpg.com/backend/opt/shrink"

    /** Track whether a compression task is already running. */
    private val isRunning = AtomicReference(false)

    /** Collect image files from selected roots (recursive for directories). */
    fun collectImageFiles(roots: List<VirtualFile>): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        for (root in roots) {
            if (root.isDirectory) {
                VfsUtil.visitChildrenRecursively(root, object : com.intellij.openapi.vfs.VirtualFileVisitor<Void>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (!file.isDirectory && isSupported(file)) result.add(file)
                        return true
                    }
                })
            } else {
                if (isSupported(root)) result.add(root)
            }
        }
        return result
    }

    private fun isSupported(file: VirtualFile): Boolean =
        file.extension?.lowercase() in SUPPORTED_EXT && file.length <= MAX_FILE_SIZE

    /** Run compression — progress bar in status bar + live console output. */
    fun compressFiles(project: Project, files: List<VirtualFile>) {
        if (files.isEmpty()) {
            TinyJpgNotifier.info(project, "No supported images found (jpg/jpeg/png/webp, ≤5MB)")
            return
        }

        // If a task is already running, notify and ignore
        if (!isRunning.compareAndSet(false, true)) {
            TinyJpgNotifier.info(project, "A compression task is already running, please wait")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "TinyJPG Compress", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val total = files.size
                var completed = 0
                var success = 0
                var failed = 0
                val errors = mutableListOf<String>()

                val console = TinyJpgConsole.get(project)
                console.clear()
                console.print("═══ TinyJPG Compress ─── $total file(s)\n")

                val executor = Executors.newFixedThreadPool(MAX_CONCURRENCY)
                val sem = Semaphore(MAX_CONCURRENCY)

                // Track results in a thread-safe way
                val lock = Any()

                val futures = files.map { file ->
                    executor.submit<java.lang.Void> {
                        sem.acquire()
                        try {
                            indicator.checkCanceled()

                            val fileName = file.name
                            val idx: Int
                            synchronized(lock) { idx = completed + 1 }

                            ApplicationManager.getApplication().invokeLater {
                                indicator.text2 = "[$idx/$total] $fileName"
                                indicator.fraction = (idx - 1).toDouble() / total.toDouble()
                            }

                            console.print("▶ [$idx/$total] $fileName ... ")

                            val result = compressOne(file)
                            if (result.isSuccess) {
                                val (inputSz, outputSz) = result.getOrThrow()
                                val ratio = if (inputSz > 0) outputSz.toDouble() / inputSz.toDouble() * 100 else 0.0
                                val saved = inputSz - outputSz
                                console.print("✓ ${fmtSize(inputSz)} → ${fmtSize(outputSz)}  " +
                                        "(${"%.1f".format(ratio)}%%, saved ${fmtSize(saved)})\n")
                                synchronized(lock) { success++ }
                            } else {
                                val err = result.exceptionOrNull()
                                console.print("✗ Failed: ${err?.message ?: "unknown error"}\n")
                                synchronized(lock) {
                                    failed++
                                    errors.add("$fileName: ${err?.message}")
                                }
                            }
                        } catch (e: java.util.concurrent.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            synchronized(lock) {
                                failed++
                                errors.add("${file.name}: ${e.message}")
                            }
                            console.print("✗ ${e.message}\n")
                        } finally {
                            sem.release()
                            synchronized(lock) {
                                completed++
                                val frac = completed.toDouble() / total.toDouble()
                                ApplicationManager.getApplication().invokeLater {
                                    indicator.fraction = frac
                                }
                            }
                        }
                        null
                    }
                }
                futures.forEach { it.get() }
                executor.shutdown()

                // Refresh files in IDE
                ApplicationManager.getApplication().invokeLater {
                    VfsUtil.markDirtyAndRefresh(false, true, true, *files.toTypedArray())
                }

                val summary = buildString {
                    appendLine()
                    appendLine("═══ TinyJPG Complete ═══")
                    appendLine("Total $total  |  Success $success  |  Failed $failed")
                    if (errors.isNotEmpty()) {
                        appendLine()
                        errors.forEach { appendLine("  ✗ $it") }
                    }
                }
                console.print(summary)

                // Notification (bottom-right balloon)
                if (failed == 0) {
                    TinyJpgNotifier.info(project, "Compress complete! $success file(s) processed")
                } else {
                    TinyJpgNotifier.warn(project, "Complete: $success success, $failed failed, $total total")
                }

                // Mark task as finished so new compression can start
                isRunning.set(false)
            }
        })
    }

    // ── HTTP logic ────────────────────────────────────────

    private data class CompressResult(val inputSize: Long, val outputSize: Long)

    private fun compressOne(file: VirtualFile): Result<CompressResult> = runCatching {
        val bytes = file.contentsToByteArray()
        val respJson = upload(bytes, mimeType(file.name))
        val outputUrl = parseOutputUrl(respJson)
        downloadAndReplace(file, outputUrl)
        val inputSize = parseInputSize(respJson)
        val outputSize = parseOutputSize(respJson)
        CompressResult(inputSize, outputSize)
    }

    /** Upload image — returns raw JSON response body. */
    private fun upload(data: ByteArray, mime: String): String {
        val conn = URI(UPLOAD_URL).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 300_000
        conn.setRequestProperty("Content-Type", mime)
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        conn.setRequestProperty("Origin", "https://tinyjpg.com")
        conn.setRequestProperty("Referer", "https://tinyjpg.com/")
        conn.setRequestProperty("Accept", "*/*")
        conn.setFixedLengthStreamingMode(data.size)

        conn.outputStream.use { it.write(data); it.flush() }

        val code = conn.responseCode
        val body = if (code == HttpURLConnection.HTTP_CREATED) {
            conn.inputStream.use { it.readBytes() }
        } else {
            (conn.errorStream?.use { it.readBytes() }) ?: ByteArray(0)
        }
        conn.disconnect()

        val bodyStr = String(body, Charsets.UTF_8)

        if (code != HttpURLConnection.HTTP_CREATED) {
            val msg = extractJsonString(bodyStr, "message") ?: "HTTP $code"
            throw RuntimeException("Upload failed: $msg")
        }
        return bodyStr
    }

    /** Download compressed file and atomically replace the original. */
    private fun downloadAndReplace(file: VirtualFile, downloadUrl: String) {
        val conn = URI(downloadUrl).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 300_000
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw RuntimeException("Download failed: HTTP $code")
        }

        val localFile = File(file.path)
        val tmp = Files.createTempFile("tinyjpg_", ".tmp")
        try {
            conn.inputStream.use { input -> Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING) }
            conn.disconnect()
            Files.move(tmp, localFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    // ── Helper: minimal JSON parsing (no Gson dependency) ──

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    /** Extract a quoted string value for a given key from simple JSON. */
    private fun extractJsonString(json: String, key: String): String? {
        val regex = "\"${key}\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    /** Extract a numeric (long) value for a given key from simple JSON. */
    private fun extractJsonLong(json: String, key: String): Long? {
        val regex = "\"${key}\"\\s*:\\s*(\\d+)".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    /** Extract "output.url" from the response. */
    private fun parseOutputUrl(json: String): String {
        // The response looks like: {"input":{"size":123},"output":{"size":456,"url":"https://..."}}
        // Find "url" inside the output object
        val outputBlock = json.substringAfter("\"output\"").substringAfter("{").substringBeforeLast("}")
        val urlRegex = "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return urlRegex.find(outputBlock)?.groupValues?.getOrNull(1)
            ?: extractJsonString(json, "url")
            ?: throw RuntimeException("Failed to parse download URL: ${json.take(200)}")
    }

    private fun parseInputSize(json: String): Long {
        // input size is nested: {"input":{"size":...}}
        val inputBlock = json.substringAfter("\"input\"").substringAfter("{").substringBefore("}")
        val regex = "\"size\"\\s*:\\s*(\\d+)".toRegex()
        return regex.find(inputBlock)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: 0L
    }

    private fun parseOutputSize(json: String): Long {
        val outputBlock = json.substringAfter("\"output\"").substringAfter("{").substringBeforeLast("}")
        val regex = "\"size\"\\s*:\\s*(\\d+)".toRegex()
        return regex.find(outputBlock)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: 0L
    }

    private fun fmtSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
