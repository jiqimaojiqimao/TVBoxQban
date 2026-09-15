package com.github.tvbox.osc.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author xuameng
 * @since 2026/9/15
 * 历史列表专用：只查轻量字段，dataJson以文件形式存储 解决大列表数据库崩溃
 */

public class AppDataManager {
    private static final int DB_FILE_VERSION = 6;
    private static final String DB_NAME = "jvhuiys";
    private static AppDataManager manager;   //xuameng搜索历史
    private static AppDataBase dbInstance;

    private AppDataManager() {
    }

    public static void init() {
        if (manager == null) {
            synchronized (AppDataManager.class) {
                if (manager == null) {
                    manager = new AppDataManager();
                }
            }
        }
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE sourceState ADD COLUMN tidSort TEXT");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `vodRecordTmp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vodId` TEXT, `updateTime` INTEGER NOT NULL, `sourceKey` TEXT, `data` BLOB, `dataJson` TEXT, `testMigration` INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS t_search (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, searchKeyWords TEXT)"); //xuameng搜索历史
            database.execSQL("CREATE INDEX IF NOT EXISTS index_t_search_searchKeyWords ON t_search (searchKeyWords)");  //xuameng搜索历史
            // Read every thing from the former Expense table
            Cursor cursor = database.query("SELECT * FROM vodRecord");

            int id;
            int vodId;
            long updateTime;
            String sourceKey;
            String dataJson;

            while (cursor.moveToNext()) {
                id = cursor.getInt(cursor.getColumnIndex("id"));
                vodId = cursor.getInt(cursor.getColumnIndex("vodId"));
                updateTime = cursor.getLong(cursor.getColumnIndex("updateTime"));
                sourceKey = cursor.getString(cursor.getColumnIndex("sourceKey"));
                dataJson = cursor.getString(cursor.getColumnIndex("dataJson"));
                database.execSQL("INSERT INTO vodRecordTmp (id, vodId, updateTime, sourceKey, dataJson, testMigration) VALUES" +
                        " ('" + id + "', '" + vodId + "', '" + updateTime + "', '" + sourceKey + "', '" + dataJson + "',0  )");
            }


            // Delete the former table
            database.execSQL("DROP TABLE vodRecord");
            // Rename the current table to the former table name so that all other code continues to work
            database.execSQL("ALTER TABLE vodRecordTmp RENAME TO vodRecord");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {  //xuameng 列表用轻量字段 名称 图片 播放到几集
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN vodName TEXT");
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN vodPic TEXT");
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN playNote TEXT");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };


    static final Migration MIGRATION_4_5 = new Migration(4, 5) {  //xuameng dataJsonPath以文件形式存储
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE vodRecord ADD COLUMN dataJsonPath TEXT");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {   //xuameng 节目源列表、播放集数 播放器类型 倒叙
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN currentPlayFlag TEXT");
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN currentPlayIndex INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN playerCfg TEXT");
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN reverseSort INTEGER NOT NULL DEFAULT 0");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static String dbPath() {
        return DB_NAME + ".v" + DB_FILE_VERSION + ".db";
    }

    public static AppDataBase get() {
        if (manager == null) {
            throw new RuntimeException("AppDataManager is no init");
        }
        if (dbInstance == null)
            dbInstance = Room.databaseBuilder(App.getInstance(), AppDataBase.class, dbPath())
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
//                  .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)     //xuameng搜索历史
                    .addMigrations(MIGRATION_3_4)     //xuameng 列表用轻量字段 名称 图片 播放到几集
                    .addMigrations(MIGRATION_4_5)     //xuameng dataJsonPath以文件形式存储
                    .addMigrations(MIGRATION_5_6)     //xuameng 节目源列表、播放集数 播放器类型 倒叙
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
//                        LOG.i("数据库第一次创建成功");
                        }

                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            super.onOpen(db);
//                        LOG.i("数据库打开成功");
                        }
                    }).allowMainThreadQueries()//可以在主线程操作
                    .build();
        return dbInstance;
    }

    public static boolean backup(File path) throws IOException {
//        if (dbInstance != null && dbInstance.isOpen()) {    //xuameng先注销不关闭数据库防止 连接池关闭崩溃
//            dbInstance.close();
//        }

        File db = App.getInstance().getDatabasePath(dbPath());
        if (db.exists()) {
            FileUtils.copyFile(db, path);
            return true;
        } else {
            return false;
        }
    }

    public static boolean restore(File path) throws IOException {
        if (dbInstance != null && dbInstance.isOpen()) {
            dbInstance.close();
        }
        File db = App.getInstance().getDatabasePath(dbPath());
        if (db.exists()) {
            db.delete();
        }
        if (!db.getParentFile().exists())
            db.getParentFile().mkdirs();
        FileUtils.copyFile(path, db);
        return true;
    }
}
