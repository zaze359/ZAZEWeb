package com.zaze.server.web;

import com.mysql.cj.xdevapi.JsonArray;

import java.util.List;

public class PageResponse {
    private long total;
    private long totalNotFiltered;
    private List<?> rows;

    public PageResponse(long total, long totalNotFiltered, List<?> rows) {
        this.total = total;
        this.totalNotFiltered = totalNotFiltered;
        this.rows = rows;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getTotalNotFiltered() {
        return totalNotFiltered;
    }

    public void setTotalNotFiltered(long totalNotFiltered) {
        this.totalNotFiltered = totalNotFiltered;
    }

    public List<?> getRows() {
        return rows;
    }

    public void setRows(List<Object> rows) {
        this.rows = rows;
    }
}
