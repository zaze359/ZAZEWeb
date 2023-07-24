package com.zaze.server.common.controller;


import java.util.List;

public class PageResponse<T> {
    private long total;
    private long totalNotFiltered;
    private List<T> rows;

    public PageResponse(long total, long totalNotFiltered, List<T> rows) {
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

    public List<T> getRows() {
        return rows;
    }

    public void setRows(List<T> rows) {
        this.rows = rows;
    }

    @Override
    public String toString() {
        return "PageResponse{" +
                "total=" + total +
                ", totalNotFiltered=" + totalNotFiltered +
                ", rows=" + rows +
                '}';
    }
}
