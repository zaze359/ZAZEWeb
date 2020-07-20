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
    // @Column(nullable = false, unique = true)
    private String url;
    @Column
    private String info;
    @Column
    private String tags;
    @Column
    private String imgUrl;
    @Column(nullable = false)
    private long createTime;
    @Column(nullable = false)
    private long updateTime;

    public Showcase() {
    }

    public Showcase(Long id, String title, String url, String info, String tags, String imgUrl, long createTime,
            long updateTime) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.info = info;
        this.tags = tags;
        this.imgUrl = imgUrl;
        this.createTime = createTime;
        this.updateTime = updateTime;
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

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }
}