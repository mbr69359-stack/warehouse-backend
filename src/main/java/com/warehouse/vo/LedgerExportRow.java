package com.warehouse.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class LedgerExportRow {

    @ExcelProperty("发生时间")
    @ColumnWidth(20)
    private String occurredAt;

    @ExcelProperty("仓库")
    @ColumnWidth(15)
    private String warehouseName;

    @ExcelProperty("商品名称")
    @ColumnWidth(30)
    private String productName;

    @ExcelProperty("SKU")
    @ColumnWidth(20)
    private String skuCode;

    @ExcelProperty("单位")
    @ColumnWidth(8)
    private String unit;

    @ExcelProperty("流水类型")
    @ColumnWidth(12)
    private String typeName;

    @ExcelProperty("变动数量")
    @ColumnWidth(12)
    private BigDecimal changeQty;

    @ExcelProperty("余量")
    @ColumnWidth(12)
    private BigDecimal balance;

    @ExcelProperty("关联单号")
    @ColumnWidth(28)
    private String documentNo;

    @ExcelProperty("操作人")
    @ColumnWidth(12)
    private String operator;

    @ExcelProperty("备注")
    @ColumnWidth(35)
    private String note;
}