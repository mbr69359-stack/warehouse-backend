package com.warehouse.vo;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class InventoryExportRow {

    @ExcelProperty("仓库")
    @ColumnWidth(15)
    private String warehouseName;

    @ExcelProperty("商品名称")
    @ColumnWidth(30)
    private String productName;

    @ExcelProperty("SKU")
    @ColumnWidth(20)
    private String skuCode;

    @ExcelProperty("当前库存_个")
    @ColumnWidth(14)
    private Integer qtyPiece;

    @ExcelProperty("当前库存_箱个")
    @ColumnWidth(16)
    private String qtyText;

    @ExcelProperty("预警值")
    @ColumnWidth(10)
    private Integer alertQty;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String statusText;

    // ── 临时字段：仅用于 Java 内计算 qtyText / statusText，不写入 Excel ──
    @ExcelIgnore
    private String warehouseType;

    @ExcelIgnore
    private Integer qtyPerBox;
}
