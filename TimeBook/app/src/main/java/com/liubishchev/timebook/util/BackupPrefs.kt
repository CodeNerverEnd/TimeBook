package com.liubishchev.timebook.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 备份相关的少量设置，只放几个标量，用 SharedPreferences 就够，不必为它建表。
 *
 * 存的是：
 *  - 上次成功备份的时间（用于「距上次满 24 小时才自动备份」）；
 *  - 自动备份开关（默认关，用户自己开）；
 *  - 用户选的备份文件夹（SAF 目录 Uri，已申请持久化读写权限）。
 */
object BackupPrefs {

    private const val FILE = "timebook_backup"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastBackupAt(context: Context): Long = sp(context).getLong(KEY_LAST_AT, 0L)

    fun setLastBackupAt(context: Context, at: Long) {
        sp(context).edit().putLong(KEY_LAST_AT, at).apply()
    }

    fun autoEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_AUTO, false)

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    fun treeUri(context: Context): String? = sp(context).getString(KEY_TREE, null)

    fun setTreeUri(context: Context, uri: String?) {
        sp(context).edit().putString(KEY_TREE, uri).apply()
    }

    private const val KEY_LAST_AT = "lastBackupAt"
    private const val KEY_AUTO = "autoBackup"
    private const val KEY_TREE = "backupTreeUri"
}
