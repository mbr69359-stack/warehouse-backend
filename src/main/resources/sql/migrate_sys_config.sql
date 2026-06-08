CREATE TABLE IF NOT EXISTS sys_config (
    `key`   VARCHAR(100) NOT NULL COMMENT '配置键',
    `value` VARCHAR(500) NOT NULL COMMENT '配置值',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注说明',
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数配置';

INSERT INTO sys_config (`key`, `value`, `remark`) VALUES
('COMMISSION_RATE_RETAIL',    '1.00', '零售提成费率（KSh/件）'),
('COMMISSION_RATE_WHOLESALE', '2.00', '批发提成费率（KSh/件）')
ON DUPLICATE KEY UPDATE `key` = `key`;