package com.warehouse.vo;

import lombok.Data;
import java.util.List;

/** 期初库存导入结果 */
@Data
public class ImportResultVO {
    /** 成功导入的行数 */
    private int successCount;
    /** 跳过/失败的行数 */
    private int failCount;
    /** 失败明细，每条格式："第N行: 原因" */
    private List<String> failDetails;
}