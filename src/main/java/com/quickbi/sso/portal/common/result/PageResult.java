package com.quickbi.sso.portal.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> implements Serializable {

    private Long total;
    private Long page;
    private Long size;
    private Long pages;
    private List<T> records;

    public static <T> PageResult<T> of(Long total, Long page, Long size, List<T> records) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setTotal(total);
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setPages((total + size - 1) / size);
        pageResult.setRecords(records);
        return pageResult;
    }
}
