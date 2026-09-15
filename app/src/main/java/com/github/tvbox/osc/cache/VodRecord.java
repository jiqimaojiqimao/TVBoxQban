package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * @author xuameng
 * @since 2026/9/15
 * 历史列表专用：只查轻量字段，dataJson以文件形式存储 解决大列表数据库崩溃
 */

@Entity(tableName = "vodRecord")
public class VodRecord implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "vodId")
    public String vodId;

    @ColumnInfo(name = "updateTime")
    public long updateTime;

    @ColumnInfo(name = "sourceKey")
    public String sourceKey;

    @ColumnInfo(name = "vodName")
    public String vodName;

    @ColumnInfo(name = "vodPic")
    public String vodPic;

    @ColumnInfo(name = "playNote")
    public String playNote;

    // ===== 新增：播放状态轻量字段 =====
    @ColumnInfo(name = "currentPlayFlag")
    public String currentPlayFlag;

    @ColumnInfo(name = "currentPlayIndex")
    public int currentPlayIndex;

    @ColumnInfo(name = "playerCfg")
    public String playerCfg;

    @ColumnInfo(name = "reverseSort")
    public int reverseSort; // boolean 用 int 存，0=false, 1=true
    // ===== 新增结束 =====

    public String dataJson;

    @ColumnInfo(name = "dataJsonPath")
    public String dataJsonPath;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
