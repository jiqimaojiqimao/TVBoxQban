package com.github.tvbox.osc.cache;

import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.data.AppDataManager;
import com.google.gson.ExclusionStrategy;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.base.App;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.List;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author xuameng
 * @since 2026/9/15
 * 历史列表专用：只查轻量字段，dataJson以文件形式存储 解决大列表数据库崩溃
 */

public class RoomDataManger {
    static ExclusionStrategy vodInfoStrategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesFlags")) {
                return true;
            }
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesMap")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    private static Gson getVodInfoGson() {
        return new GsonBuilder().addSerializationExclusionStrategy(vodInfoStrategy).create();
    }

    //xuameng改：写入时存轻量字段
    public static void insertVodRecord(String sourceKey, VodInfo vodInfo) {
        VodRecordDao dao = AppDataManager.get().getVodRecordDao();
        Integer existingId = dao.getVodRecordId(sourceKey, vodInfo.id);

        VodRecord record = new VodRecord();
        if (existingId != null) {
            record.setId(existingId);
        }

        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.vodName = vodInfo.name;
        record.vodPic = vodInfo.pic;
        record.playNote = vodInfo.playNote;

        // ===== 新增：写播放状态 =====
        record.currentPlayFlag = vodInfo.currentPlayFlag;
        record.currentPlayIndex = vodInfo.currentPlayIndex;
        record.playerCfg = vodInfo.playerCfg != null ? vodInfo.playerCfg : "";
        record.reverseSort = vodInfo.reverseSort ? 1 : 0;
        // ===== 新增结束 =====

        // JSON 写文件，路径存库
        String json = getVodInfoGson().toJson(vodInfo);
        String fileName = sourceKey + "_" + vodInfo.id + ".json";
        File dir = new File(App.getInstance().getFilesDir(), "vod_record");
        if (!dir.exists()) dir.mkdirs();
        File jsonFile = new File(dir, fileName);
        try {
            FileWriter writer = new FileWriter(jsonFile);
            writer.write(json);
            writer.close();
            record.dataJsonPath = jsonFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            record.dataJson = json; // 兜底
        }

        dao.insert(record);
    }

    public static VodInfo getVodInfo(String sourceKey, String vodId) {
        VodRecordPath recordPath = AppDataManager.get().getVodRecordDao().getVodRecordPath(sourceKey, vodId);
        if (recordPath == null || TextUtils.isEmpty(recordPath.dataJsonPath)) {
            return null;
        }

        File jsonFile = new File(recordPath.dataJsonPath);
        if (!jsonFile.exists()) {
            return null;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            VodInfo vodInfo = getVodInfoGson().fromJson(sb.toString(), new TypeToken<VodInfo>() {}.getType());
            if (vodInfo.name == null) return null;
            return vodInfo;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void deleteVodRecord(String sourceKey, VodInfo vodInfo) {
        VodRecordDao dao = AppDataManager.get().getVodRecordDao();
    
        VodRecordPath recordPath = dao.getVodRecordPath(sourceKey, vodInfo.id);
        if (recordPath != null && !TextUtils.isEmpty(recordPath.dataJsonPath)) {
            File jsonFile = new File(recordPath.dataJsonPath);
            if (jsonFile.exists()) {
                jsonFile.delete();
            }
        }
    
        dao.deleteBySourceAndVodId(sourceKey, vodInfo.id);
    }

    //xuameng 改：历史列表用摘要查询，不反序列化 dataJson
    public static List<VodInfo> getAllVodRecord(int limit) {
        int count = AppDataManager.get().getVodRecordDao().getCount();
        Integer index = Hawk.get(HawkConfig.HISTORY_NUM, 0);
        int hisNum = HistoryHelper.getHisNum(index);
        if (count > hisNum) {
            AppDataManager.get().getVodRecordDao().reserver(hisNum);
        }    

        List<VodRecordListSummary> summaryList =
                AppDataManager.get().getVodRecordDao().getHistorySummary(limit);

        List<VodInfo> vodInfoList = new ArrayList<>();
        for (VodRecordListSummary s : summaryList) {
            VodInfo info = new VodInfo();
            info.id = s.vodId;
            info.name = s.vodName;
            info.pic = s.vodPic;
            info.sourceKey = s.sourceKey;
            info.playNote = s.playNote;

            SourceBean sourceBean = ApiConfig.get().getSource(info.sourceKey);
            if (sourceBean != null && info.name != null) {
                vodInfoList.add(info);
            }
        }
        return vodInfoList;
    }

    public static void insertVodCollect(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            return;
        }
        record = new VodCollect();
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.name = vodInfo.name;
        record.pic = vodInfo.pic;
        AppDataManager.get().getVodCollectDao().insert(record);
    }

    public static void deleteVodCollect(int id) {
        AppDataManager.get().getVodCollectDao().delete(id);
    }

    public static void deleteVodCollect(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            AppDataManager.get().getVodCollectDao().delete(record);
        }
    }
    
    public static void deleteVodCollectAll() {
        AppDataManager.get().getVodCollectDao().deleteAll();
    }

    public static void deleteVodRecordAll() {
        AppDataManager.get().getVodRecordDao().deleteAll();
    }

    public static boolean isVodCollect(String sourceKey, String vodId) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodId);
        return record != null;
    }

    public static List<VodCollect> getAllVodCollect() {
        return AppDataManager.get().getVodCollectDao().getAll();
    }
}
