package com.liubishchev.timebook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskItem::class, TimeRecord::class, Tag::class, ItemTag::class,
        PlanTarget::class, PlanDoc::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskItemDao(): TaskItemDao
    abstract fun timeRecordDao(): TimeRecordDao
    abstract fun tagDao(): TagDao
    abstract fun planTargetDao(): PlanTargetDao
    abstract fun planDocDao(): PlanDocDao

    companion object {
        private const val DB_NAME = "timebook.db"

        /** v1 -> v2：加入目标时长与提醒时刻，老数据默认「不提醒」。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_item ADD COLUMN targetMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE time_record ADD COLUMN targetMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE time_record ADD COLUMN remindAt INTEGER")
            }
        }

        /**
         * v2 -> v3：加入标签体系（一个事项可挂多个标签）。
         * 老数据不能变成「无标签」的孤儿，自动建一个「未分类」标签把它们全挂上，之后随您改名或拆分。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tag` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`archived` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `item_tag` (" +
                        "`itemId` INTEGER NOT NULL, " +
                        "`tagId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`itemId`, `tagId`), " +
                        "FOREIGN KEY(`itemId`) REFERENCES `task_item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`tagId`) REFERENCES `tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_tag_itemId` ON `item_tag` (`itemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_tag_tagId` ON `item_tag` (`tagId`)")

                // 给存量事项补一个「未分类」标签
                db.execSQL(
                    "INSERT INTO `tag` (`name`, `colorArgb`, `note`, `archived`, `createdAt`, `sortOrder`) " +
                        "VALUES ('未分类', ${DEFAULT_TAG_COLOR}, '', 0, ${System.currentTimeMillis()}, 0)"
                )
                db.execSQL(
                    "INSERT INTO `item_tag` (`itemId`, `tagId`) " +
                        "SELECT `id`, (SELECT `id` FROM `tag` WHERE `name` = '未分类' LIMIT 1) FROM `task_item`"
                )
            }
        }

        /** 未分类标签的默认颜色（灰）。 */
        const val DEFAULT_TAG_COLOR = 0xFF6B7280.toInt()

        /** v3 -> v4：加入「计划」，即某周期内某标签打算花多少分钟。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_target` (" +
                        "`tagId` INTEGER NOT NULL, " +
                        "`periodType` TEXT NOT NULL, " +
                        "`periodKey` TEXT NOT NULL, " +
                        "`targetMinutes` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`tagId`, `periodType`, `periodKey`))"
                )
            }
        }

        /**
         * v4 -> v5：计划从「只到标签」升级为「标签 + 具体事项」两级，并加入计划档案。
         *
         * - plan_target 的 tagId 改名 refId 并加 targetType；**存量计划全部按 TAG 迁移**，
         *   所以老用户的月度 / 年度计划一条都不会丢。
         * - 新增 plan_doc 存每期的总结与归档快照；老数据没有归档记录，保持为空即「本期」。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_target_v5` (" +
                        "`refId` INTEGER NOT NULL, " +
                        "`targetType` TEXT NOT NULL, " +
                        "`periodType` TEXT NOT NULL, " +
                        "`periodKey` TEXT NOT NULL, " +
                        "`targetMinutes` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`refId`, `targetType`, `periodType`, `periodKey`))"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `plan_target_v5` " +
                        "(`refId`, `targetType`, `periodType`, `periodKey`, `targetMinutes`) " +
                        "SELECT `tagId`, 'TAG', `periodType`, `periodKey`, `targetMinutes` FROM `plan_target`"
                )
                db.execSQL("DROP TABLE `plan_target`")
                db.execSQL("ALTER TABLE `plan_target_v5` RENAME TO `plan_target`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_doc` (" +
                        "`periodType` TEXT NOT NULL, " +
                        "`periodKey` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`summary` TEXT NOT NULL, " +
                        "`closedAt` INTEGER, " +
                        "`snapshotJson` TEXT NOT NULL, " +
                        "PRIMARY KEY(`periodType`, `periodKey`))"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
