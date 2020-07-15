package com.zaze.server.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Showcase {
    // "showcaseTitle": "黑色沙漠台服",
    // "showcaseTitleUrl": "https://www.tw.playblackdesert.com/",
    // "showcaseInfo": "Pearl Abyss MMO的開始 黑色沙漠
    // Remastered，更加真實的冒險正在等待著您。超越真實感，與玩家共享感動的黑色沙漠。",
    // "showcaseTag": "Game,官网",
    // "showcaseTagUrl": "https://www.tw.playblackdesert.com/",
    // "showcaseImg":
    // "https://s1.pearlcdn.com/TW/contents/img/common/header_logo_tw.png",
    // "showcaseTime": "2017-09-01"

    @Id
    @GeneratedValue
    private Long id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, unique = true)
    private String url;
    @Column
    private String info;
    @Column
    private String tag;
    @Column
    private String imgUrl;
    @Column(nullable = false)
    private long time;

    public Showcase() {
    }

    public Showcase(Long id, String title, String url, String info, String tag, String imgUrl, long time) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.info = info;
        this.tag = tag;
        this.imgUrl = imgUrl;
        this.time = time;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

}