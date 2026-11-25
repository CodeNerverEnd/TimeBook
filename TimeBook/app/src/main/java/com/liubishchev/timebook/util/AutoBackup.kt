package com.liubishchev.timebook.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.liubishchev.timebook.data.AppDatabase

/**
 * 自动备份：把备份文件写进用户选的文件夹，每天一份，只留最近 5 份。
 *
 * 为什么不引 WorkManager：这里只需要「打开 App / 结束一次计时」时检查一下
 * 距上次是否满 24 小时，写一个几百 KB 的 JSON 是毫秒级操作，
 * 引一个后台调度框架反而多耗电、多一个失败点。
 *
 * 目录用 SAF（ACTION_OPEN_DOCUMENT_TREE）授权并持久化，
 * 只用系统自带的 DocumentsContract，不额外引依赖。
 */
object AutoBackup {

    private const val INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    private const val KEEP_COUNT = 5

    /** 满足「开着自动备份 + 距上次满一天 + 目录还在」才真的写。 */
    suspend fun maybeRun(context: Context) {
        if (!BackupPrefs.autoEnabled(context)) return
        val tree = BackupPrefs.treeUri(context)?.let { Uri.parse(it) } ?: return
        val now = System.currentTimeMillis()
        val last = BackupPrefs.lastBackupAt(context)
        if (last > 0L && now - last < INTERVAL_MILLIS) return
        runCatching { writeBackup(context, tree, now) }
    }

    /** 立刻写一份，返回文件名；失败抛异常由调用方决定怎么提示。 */
    suspend fun writeBackup(
        context: Context,
        tree: Uri,
        now: Long = System.currentTimeMillis()
    ): String {
        val json = BackupJson.build(AppDatabase.get(context))
        val name = BackupJson.fileName(now)
        val resolver = context.contentResolver
        val parent = treeDocumentUri(tree)
        val uri = DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: error("所选文件夹里无法创建文件")
        resolver.openOutputStream(uri, "wt")?.use { os ->
            os.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("备份文件写入失败")
        BackupPrefs.setLastBackupAt(context, now)
        runCatching { prune(context, tree) }
        return name
    }

    private fun treeDocumentUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** 文件名带 yyyyMMdd-HHmm，字典序即时间序，倒序丢老的。 */
    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val parent = treeDocumentUri(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getDocumentId(parent)
        )
        val found = ArrayList<Pair<String, Uri>>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(BackupJson.FILE_PREFIX) && name.endsWith(".json")) {
                    found.add(name to DocumentsContract.buildDocumentUriUsingTree(tree, id))
                }
            }
        }
        found.sortedByDescending { it.first }
            .drop(KEEP_COUNT)
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.second) } }
    }
}
