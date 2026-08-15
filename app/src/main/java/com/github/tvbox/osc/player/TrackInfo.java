package com.github.tvbox.osc.player;

import java.util.ArrayList;
import java.util.List;

public class TrackInfo {
    private List<TrackInfoBean> audio;
    private List<TrackInfoBean> video; //xuameng视轨信息
    private List<TrackInfoBean> subtitle;

    public TrackInfo() {
        audio = new ArrayList<>();
        video = new ArrayList<>();  //xuameng视轨信息
        subtitle = new ArrayList<>();
    }

    public List<TrackInfoBean> getAudio() {
        return audio;
    }

    public int getAudioSelected(boolean track) {
        return getSelected(audio, track);
    }

    public int getSubtitleSelected(boolean track) {
        return getSelected(subtitle, track);
    }


    public int getSelected(List<TrackInfoBean> list, boolean track) {     
        // xuameng如果列表只有一个音轨，直接返回该元素的相应ID
        if (list.size() == 1) {  
            TrackInfoBean singleTrack = list.get(0);
            return track ? singleTrack.trackId : 0;
        }
    
        // 多个元素时保持原有逻辑
        int i = 0;
        for (TrackInfoBean trackInfoBean : list) {
            if (trackInfoBean.selected) return track ? trackInfoBean.trackId : i;
            i++;
        }
        return 99999;
    }


    public void addAudio(TrackInfoBean audio) {
        this.audio.add(audio);
    }

    public List<TrackInfoBean> getSubtitle() {
        return subtitle;
    }

    public void addSubtitle(TrackInfoBean subtitle) {
        this.subtitle.add(subtitle);
    }

    public List<TrackInfoBean> getVideo() { //xuameng视轨信息
        return video;
    }

    public int getVideoSelected(boolean track) { //xuameng视轨信息
        return getSelected(video, track);
    }

    public void addVideo(TrackInfoBean video) { //xuameng视轨信息
        this.video.add(video);
    }

}
