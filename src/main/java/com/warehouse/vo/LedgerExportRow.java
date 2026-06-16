package com.warehouse.vo;

import com.alibaba.excel.annotation.ExcelIgnore;
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

    @ExcelProperty("流水类型")
    @ColumnWidth(12)
    private String typeName;

    @ExcelProperty("变动数量_个")
    @ColumnWidth(14)
    private BigDecimal changeQtyPiece;

    @ExcelProperty("变动数量_箱个")
    @ColumnWidth(16)
    private String changeQtyText;

    @ExcelProperty("关联单号")
    @ColumnWidth(28)
    private String documentNo;

    @ExcelProperty("操作人")
    @ColumnWidth(12)
    private String operator;

    @ExcelProperty("备注")
    @ColumnWidth(35)
    private String note;

    // ── 临时字段：仅用于 Java 内计算箱/个换算，不写入 Excel ──
    @ExcelIgnore
    private BigDecimal changeQty;     // 原始变动数量（按记账单位）

    @ExcelIgnore
    private String unit;              // 商品基础单位（仅占位映射，不导出）

    @ExcelIgnore
    private String qtyUnit;           // 记账单位 BOX / PIECE

    @ExcelIgnore
    private Integer qtyPerBox;        // 每箱数量

    @ExcelIgnore
    private String warehouseType;     // 仓库类型 BOX / PIECE

    @ExcelIgnore
    private BigDecimal balance;       // 余量（不再导出，避免混单位误导）
}
