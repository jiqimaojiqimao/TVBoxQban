
package com.github.tvbox.osc.data;

import com.github.tvbox.osc.cache.SearchHistory;

import java.util.ArrayList;
import java.util.List;

public class SearchPresenter {

    public boolean keywordsExist(String keyword) {
        return DbHelper.checkKeyWords(keyword);
    }

    public ArrayList<SearchHistory> getSearchHistory() {
        return DbHelper.getAllHistory();
    }

    public void addKeyWordsTodb(String keyword) {
        DbHelper.addKeywords(keyword);
    }

    public void clearSearchHistory() {
        DbHelper.clearKeywords();
    }
    
    /**xuameng
     * 根据搜索关键词删除单条历史记录
     * @param keyword 要删除的搜索关键词
     * @return 删除是否成功
     */
    public boolean deleteKeyWordsFromDb(String keyword) {
        try {
            // 1. 通过 DbHelper 查询匹配该关键词的记录
            ArrayList<SearchHistory> historiesToDelete = (ArrayList<SearchHistory>) DbHelper.getByKeywords(keyword);
            
            // 2. 检查查询结果
            if (historiesToDelete != null && !historiesToDelete.isEmpty()) {
                // 3. 使用 DbHelper 删除查询到的第一条匹配记录
                DbHelper.deleteSingleHistory(historiesToDelete.get(0)); 
                return true; // 删除成功
            }
            // 4. 如果没有找到匹配的记录
            return false;
        } catch (Exception e) {
            // 5. 捕获异常并打印堆栈信息，返回删除失败
            e.printStackTrace();
            return false; // 删除过程中发生异常，返回失败
        }
    }
}
