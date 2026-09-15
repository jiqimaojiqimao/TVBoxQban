package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * @author xuameng
 * @since 2026/9/15
 * 历史列表专用：只查轻量字段，dataJson以文件形式存储 解决大列表数据库崩溃
 */

@Dao
public interface VodRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(VodRecord record);

    @Query("SELECT id FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId LIMIT 1")
    Integer getVodRecordId(String sourceKey, String vodId);

    @Query("SELECT id, dataJsonPath FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId LIMIT 1")
    VodRecordPath getVodRecordPath(String sourceKey, String vodId);

    // 播放状态摘要（带 currentPlayFlag 等）
    @Query("SELECT id, vodId, updateTime, sourceKey, vodName, vodPic, playNote, currentPlayFlag, currentPlayIndex, playerCfg, reverseSort FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId LIMIT 1")
    VodRecordSummary getVodRecordSummary(String sourceKey, String vodId);

    // 历史列表用（只查轻量字段，返回列表专用 POJO）
    @Query("SELECT id, vodId, updateTime, sourceKey, vodName, vodPic, playNote FROM vodRecord ORDER BY updateTime DESC LIMIT :size")
    List<VodRecordListSummary> getHistorySummary(int size);

    @Query("SELECT count(*) FROM vodRecord")
    int getCount();

    @Query("DELETE FROM vodRecord")
    void deleteAll();

    @Query("DELETE FROM vodRecord WHERE id NOT IN (SELECT id FROM vodRecord ORDER BY updateTime DESC LIMIT :size)")
    int reserver(int size);

    @Query("DELETE FROM vodRecord WHERE `sourceKey`=:sourceKey")
    void deleteBySourceKey(String sourceKey);

    @Query("DELETE FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId")
    void deleteBySourceAndVodId(String sourceKey, String vodId);
}
