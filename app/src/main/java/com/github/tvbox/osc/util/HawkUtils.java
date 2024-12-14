package com.github.tvbox.osc.util;

import android.content.Context;
import com.github.tvbox.osc.R;
import com.orhanobut.hawk.Hawk;

import java.util.List;

public class HawkUtils {
    
    public static String getLastLiveChannelGroup() {
        return Hawk.get(HawkConfig.LIVE_CHANNEL_GROUP, "");
    }

    public static void setLastLiveChannelGroup(String group) {
        Hawk.put(HawkConfig.LIVE_CHANNEL_GROUP, group);
    }

    public static String getLastLiveChannel() {
        return Hawk.get(HawkConfig.LIVE_CHANNEL, "");
    }

    public static void setLastLiveChannel(String channel) {
        Hawk.put(HawkConfig.LIVE_CHANNEL, channel);
    }
}
