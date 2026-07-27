package com.github.tvbox.osc.util

import com.github.tvbox.osc.bean.LiveChannelGroup

/**
 *Automatic generated
 *@author Created by  xuameng
 *@date 27/7/2026 22:22
 */
object JavaUtil {
    @JvmStatic
    fun findLiveLastChannel(liveChannelGroupList: List<LiveChannelGroup>): Pair<Int, Int> {
        val lastChannelName = HawkUtils.getLastLiveChannel()
        val lastChannelGroupName = HawkUtils.getLastLiveChannelGroup()

        return liveChannelGroupList
            .find { it.groupName == lastChannelGroupName }
            ?.let { group ->
                // xuameng 关键修复：liveChannels 可能为 null
                group.liveChannels?.find { it.channelName == lastChannelName }
                    ?.let { channel ->
                        group.groupIndex to channel.channelIndex
                    }
            }
            ?: run {
                var noPassWordGroupIndex = -1

                liveChannelGroupList.forEach { group ->
                    // xuameng 关键修复：安全调用 + 空列表兜底
                    group.liveChannels?.forEach { channel ->
                        if (channel.channelName == lastChannelName) {
                            return group.groupIndex to channel.channelIndex
                        }
                    }

                    if (noPassWordGroupIndex == -1 && group.groupPassword.isEmpty()) {
                        noPassWordGroupIndex = group.groupIndex
                    }
                }

                if (noPassWordGroupIndex == -1) noPassWordGroupIndex = 0
                noPassWordGroupIndex to 0
            }
    }
}
