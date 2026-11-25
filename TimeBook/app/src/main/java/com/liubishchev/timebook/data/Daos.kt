package com.liubishchev.timebook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskItemDao {

    @Query("SELECT * FROM task_item ORDER BY archived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TaskItem>>

    /** 导出备份时直查用。 */
    @Query("SELECT * FROM task_item")
    suspend fun findAll(): List<TaskItem>

    @Query("SELECT * FROM task_item WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TaskItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TaskItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskItem>)

    @Update
    suspend fun update(item: TaskItem)

    @Delete
    suspend fun delete(item: TaskItem)

    @Query("DELETE FROM task_item")
    suspend fun deleteAll()
}

@Dao
interface TagDao {

    @Query("SELECT * FROM tag ORDER BY archived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Tag>>

    /** 导出备份时直查用。 */
    @Query("SELECT * FROM tag")
    suspend fun findAll(): List<Tag>

    @Query("SELECT * FROM item_tag")
    suspend fun findAllLinks(): List<ItemTag>

    /** 全部「事项 ↔ 标签」关联，数据量小，直接全量放内存算分组。 */
    @Query("SELECT * FROM item_tag")
    fun observeLinks(): Flow<List<ItemTag>>

    @Query("SELECT * FROM tag WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Tag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<Tag>)

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    // -------------------------------------------------- 事项 ↔ 标签 关联

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkAll(links: List<ItemTag>)

    @Query("DELETE FROM item_tag WHERE itemId = :itemId")
    suspend fun unlinkItem(itemId: Long)

    @Query("SELECT COUNT(*) FROM item_tag WHERE tagId = :tagId")
    suspend fun countItemsOfTag(tagId: Long): Int

    @Query("SELECT COUNT(*) FROM item_tag WHERE itemId = :itemId")
    suspend fun countTagsOfItem(itemId: Long): Int

    @Query("DELETE FROM item_tag")
    suspend fun deleteAllLinks()

    @Query("DELETE FROM tag")
    suspend fun deleteAll()
}

@Dao
interface PlanTargetDao {

    @Query("SELECT * FROM plan_target")
    fun observeAll(): Flow<List<PlanTarget>>

    /** 导出备份时直查用，不依赖界面 Flow 是否已收到数据。 */
    @Query("SELECT * FROM plan_target")
    suspend fun findAll(): List<PlanTarget>

    @Query("SELECT * FROM plan_target WHERE periodType = :type AND periodKey = :key")
    suspend fun findPeriod(type: String, key: String): List<PlanTarget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: PlanTarget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(targets: List<PlanTarget>)

    /** 删标签 / 删事项时清掉它自己的计划目标（另一种类型的保留）。 */
    @Query("DELETE FROM plan_target WHERE refId = :refId AND targetType = :targetType")
    suspend fun deleteByRef(refId: Long, targetType: String)

    @Query(
        "DELETE FROM plan_target WHERE refId = :refId AND targetType = :targetType " +
            "AND periodType = :periodType AND periodKey = :periodKey"
    )
    suspend fun deleteOne(refId: Long, targetType: String, periodType: String, periodKey: String)

    @Query("DELETE FROM plan_target WHERE periodType = :type AND periodKey = :key")
    suspend fun deletePeriod(type: String, key: String)

    @Query("DELETE FROM plan_target")
    suspend fun deleteAll()
}

/**
 * 计划档案：只在「写总结并归档 / 取消归档 / 改总结」时写入。
 * 没有记录 = 该期还没归档，属于「本期」。
 */
@Dao
interface PlanDocDao {

    @Query("SELECT * FROM plan_doc")
    fun observeAll(): Flow<List<PlanDoc>>

    @Query("SELECT * FROM plan_doc")
    suspend fun findAll(): List<PlanDoc>

    @Query("SELECT * FROM plan_doc WHERE periodType = :type AND periodKey = :key LIMIT 1")
    suspend fun find(type: String, key: String): PlanDoc?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: PlanDoc)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(docs: List<PlanDoc>)

    @Query("DELETE FROM plan_doc WHERE periodType = :type AND periodKey = :key")
    suspend fun delete(type: String, key: String)

    @Query("DELETE FROM plan_doc")
    suspend fun deleteAll()
}

@Dao
interface TimeRecordDao {

    @Query("SELECT * FROM time_record ORDER BY startAt DESC")
    fun observeAll(): Flow<List<TimeRecord>>

    /** 导出备份时直查用。 */
    @Query("SELECT * FROM time_record")
    suspend fun findAll(): List<TimeRecord>

    @Query("SELECT * FROM time_record WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    suspend fun findRunning(): TimeRecord?

    /** 只改备注时用：先取出整条，避免整行覆盖掉别处刚改的字段。 */
    @Query("SELECT * FROM time_record WHERE id = :id")
    suspend fun findById(id: Long): TimeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TimeRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TimeRecord>)

    @Update
    suspend fun update(record: TimeRecord)

    @Query("UPDATE time_record SET endAt = :endAt WHERE id = :id")
    suspend fun close(id: Long, endAt: Long)

    @Query("DELETE FROM time_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM time_record WHERE itemId = :itemId")
    suspend fun countByItem(itemId: Long): Int

    @Query("DELETE FROM time_record")
    suspend fun deleteAll()
}
