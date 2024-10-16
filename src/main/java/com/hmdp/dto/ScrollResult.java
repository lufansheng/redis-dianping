package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    //? : 以后查询别的滚动分页也同样适用
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
