-- 新增客户档案表
CREATE TABLE IF NOT EXISTS customer (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL COMMENT '客户名称',
  contact     VARCHAR(50) COMMENT '联系人',
  phone       VARCHAR(20) COMMENT '联系电话',
  address     VARCHAR(200) COMMENT '地址',
  remark      VARCHAR(500) COMMENT '备注',
  status      INT DEFAULT 1 COMMENT '状态：1合作中 0已停止',
  deleted     INT DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间'
) COMMENT '客户档案表';

-- out_order 表新增客户关联列
ALTER TABLE out_order ADD COLUMN IF NOT EXISTS customer_id BIGINT NULL COMMENT '关联客户ID' AFTER warehouse_id;