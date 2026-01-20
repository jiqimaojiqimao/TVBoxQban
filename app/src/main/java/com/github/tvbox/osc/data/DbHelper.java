
package com.github.tvbox.osc.data;

import com.github.tvbox.osc.cache.SearchHistory;

import java.util.ArrayList;
import java.util.List;

/**
 * creator huangyong
 * createTime 2018/12/17 下午6:39
 * path com.nick.movie.db.dao
 * description:中介类
 */
public class DbHelper {

    public static ArrayList<SearchHistory> getAllHistory() {
        ArrayList<SearchHistory> searchHistories = (ArrayList<SearchHistory>) AppDataManager.get().getSearchDao().getAll();
        if (searchHistories != null && searchHistories.size() > 0) {
            return searchHistories;
        } else {
            return new ArrayList<>();
        }
    }

    public static boolean checkKeyWords(String keyword) {
        ArrayList<SearchHistory> byKeywords = (ArrayList<SearchHistory>) AppDataManager.get().getSearchDao().getByKeywords(keyword);
        return byKeywords != null && byKeywords.size() > 0;
    }

    public static void addKeywords(String keyword) {
        ArrayList<SearchHistory> allHistory = getAllHistory();
        if (allHistory.size() > 29) {
            AppDataManager.get().getSearchDao().delete(allHistory.get(0));
        }

        SearchHistory searchHistory = new SearchHistory();
        searchHistory.searchKeyWords = keyword;
        AppDataManager.get().getSearchDao().insert(searchHistory);
    }

    public static void clearKeywords() {
        ArrayList<SearchHistory> allHistory = getAllHistory();
        if (allHistory != null && allHistory.size() > 0) {
            for (SearchHistory history : allHistory) {
                AppDataManager.get().getSearchDao().delete(history);
            }
        }
    }
    
    /**xuameng
     * 根据关键词获取搜索历史记录列表
     * @param keyword 搜索关键词
     * @return 匹配的搜索历史记录列表
     */
    public static List<SearchHistory> getByKeywords(String keyword) {
        return AppDataManager.get().getSearchDao().getByKeywords(keyword);
    }
    
    /**
     * 删除单个搜索历史记录
     * @param history 要删除的搜索历史记录对象
     */
    public static void deleteSingleHistory(SearchHistory history) {
        if (history != null) {
            AppDataManager.get().getSearchDao().delete(history);
        }
    }
}
