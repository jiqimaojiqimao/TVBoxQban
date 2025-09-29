package com.github.tvbox.osc.bean;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Objects;

import xyz.doikki.videoplayer.player.VideoView;

public class LivePlayerManager {
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;

    public void init(VideoView videoView) {
        try {
            if (HawkConfig.intLIVEPLAYTYPE){
                defaultPlayerConfig.put("pl", Hawk.get(HawkConfig.LIVE_PLAY_TYPE, 1));   //xuameng升级直播JSON中可以指定播放器类型
            }else{
                defaultPlayerConfig.put("pl", Hawk.get(HawkConfig.PLAY_TYPE, 0));  //xuameng升级直播JSON没有指定，默认跟随设置
            }
            defaultPlayerConfig.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "软解码"));
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));

            if (!currentPlayerConfig.has("pr")) {
                defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));  //xuameng 渲染设置
            }
            if (!currentPlayerConfig.has("exocode")) {
                defaultPlayerConfig.put("exocode", 0);      //xuameng exo动态解码  大于0为选择
			    Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 0);  // xuameng exo动态解码 大于0为选择
            }
            if (!currentPlayerConfig.has("music")) {
                defaultPlayerConfig.put("music", Hawk.get(HawkConfig.LIVE_MUSIC_ANIMATION, false));   //xuameng音乐播放动画设置
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(VideoView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getLiveChannelPlayer(VideoView videoView, String channelName) {
        JSONObject playerConfig = Hawk.get(channelName, null);
        if (playerConfig == null) {
            if (!currentPlayerConfig.toString().equals(defaultPlayerConfig.toString()))
                getDefaultLiveChannelPlayer(videoView);
            return;
        }
        if (playerConfig.toString().equals(currentPlayerConfig.toString()))
            return;

        try {
            if (playerConfig.getInt("pl") == currentPlayerConfig.getInt("pl")
                    && playerConfig.getInt("pr") == currentPlayerConfig.getInt("pr")
                    && playerConfig.getString("ijk").equals(currentPlayerConfig.getString("ijk"))) {
                videoView.setScreenScaleType(playerConfig.getInt("sc"));
            } else {
                PlayerHelper.updateCfg(videoView, playerConfig);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    public int getLivePlayerType() {
        int playerTypeIndex = 0;
        try {
            int playerType = currentPlayerConfig.getInt("pl");
            String ijkCodec = currentPlayerConfig.getString("ijk");
            switch (playerType) {
                case 0:
                    playerTypeIndex = 0;
                    break;
                case 1:
                    if (ijkCodec.equals("硬解码"))
                        playerTypeIndex = 1;
                    else
                        playerTypeIndex = 2;
                    break;
                case 2:
                    boolean exocode=Hawk.get(HawkConfig.EXO_PLAYER_DECODE, false);  //xuameng exo解码默认设置
                    int exoSelect = Hawk.get(HawkConfig.EXO_PLAY_SELECTCODE, 0);  //xuameng exo解码动态选择
                    try {
                        // 安全获取配置值
                        if (currentPlayerConfig.has("exocode")) {
                            exoSelect = currentPlayerConfig.getInt("exocode");     //xuameng exo解码动态选择 0默认设置 1硬解 2软解
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (exoSelect > 0){
                        // xuameng EXO 动态选择解码 存储选择状态
                        if (exoSelect == 1) {
                            Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 1);  // 硬解码标记存储
                            playerTypeIndex = 3;
                        } else {
                            Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 2);  // 软解码标记存储
                            playerTypeIndex = 4;
                        }
                    }else {
                        playerTypeIndex = exocode ? 4 : 3;         //xuameng EXO获取默认设置
                    }
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return playerTypeIndex;
    }

    public int getLivePlayerScale() {
        try {
            return currentPlayerConfig.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getLivePlayrender() {   //xuameng 获取渲染方式
        int pr = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        try {
            return currentPlayerConfig.getInt("pr");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return pr;
    }

    public boolean getLivePlaymusic() {   //xuameng 获取柱状图设置
        // 优先使用Hawk配置
        boolean musicType = Hawk.get(HawkConfig.LIVE_MUSIC_ANIMATION, false);
        try {
            // 严格校验JSON结构
            if (currentPlayerConfig != null && 
                currentPlayerConfig.has("music") &&
                currentPlayerConfig.get("music") instanceof Boolean) {
                return currentPlayerConfig.getBoolean("music");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return musicType;
    }


    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName) {
        JSONObject playerConfig = currentPlayerConfig;
        try {
            switch (playerType) {
                case 0:
                    playerConfig.put("pl", 0);      //xuameng系统播放器
                    playerConfig.put("ijk", "软解码");
                    break;
                case 1:
                    playerConfig.put("pl", 1);     //xuamengijk播放器
                    playerConfig.put("ijk", "硬解码");
                    break;
                case 2:
                    playerConfig.put("pl", 1);
                    playerConfig.put("ijk", "软解码");
                    break;
                case 3:
                    playerConfig.put("pl", 2);         //exo 播放器
                    playerConfig.put("exocode", 1);   //xuameng EXO硬解 动态设置
                    Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 1);
                    playerConfig.put("ijk", "软解码");
                    break;
                case 4:
                    playerConfig.put("pl", 2);
                    playerConfig.put("exocode", 2);       //xuameng EXO软解 动态设置
                    Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 2);
                    playerConfig.put("ijk", "软解码");
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    public void changeLivePlayerScale(@NonNull VideoView videoView, int playerScale, String channelName){
        videoView.setScreenScaleType(playerScale);

        JSONObject playerConfig = currentPlayerConfig;
        try {
            playerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    public void changeLivePlayerRender(VideoView videoView, int RenderType, String channelName) {  //xuameng 设置渲染方式
        JSONObject playerConfig = currentPlayerConfig;
        try {
            switch (RenderType) {
                case 0:
                    playerConfig.put("pr", 0);   //xuameng Texture
                    break;
                case 1:
                    playerConfig.put("pr", 1);  //xuameng surface
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    public void changeLivePlayerMusic(VideoView videoView, int MusicType, String channelName) {  //xuameng 柱状图
        JSONObject playerConfig = currentPlayerConfig;
        try {
            switch (MusicType) {
                case 0:
                    playerConfig.put("music", true);  //xuameng 柱状图打开
                    break;
                case 1:
                    playerConfig.put("music", false);  //xuameng 柱状图关闭
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }
}
