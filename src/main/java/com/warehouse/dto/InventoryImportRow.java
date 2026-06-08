package com.warehouse.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** Excel 导入期初库存 —— 每行对应的数据对象 */
@Data
public class InventoryImportRow {

    /** 商品 SKU 码，必须与系统中的 sku_code 完全一致 */
    @ExcelProperty("SKU码")
    private String skuCode;

    /** 仓库名称，必须与系统中的仓库名称完全一致 */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /** 期初数量，必须为正整数 */
    @ExcelProperty("数量")
    private Integer qty;
}