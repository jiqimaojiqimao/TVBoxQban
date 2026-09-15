package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;

/**
 * @author xuameng
 * @since 2026/9/15
 * 历史列表专用：只查轻量字段，dataJson以文件形式存储 解决大列表数据库崩溃
 */

public class VodRecordPath {
    @ColumnInfo(name = "id")
    public int id;

    @ColumnInfo(name = "dataJsonPath")
    public String dataJsonPath;
}