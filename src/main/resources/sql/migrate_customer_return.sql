CREATE TABLE IF NOT EXISTS customer_return (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exchange_no VARCHAR(30) UNIQUE NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    remark VARCHAR(500),
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100) NOT NULL DEFAULT '',
    out_order_id BIGINT NULL
);

CREATE TABLE IF NOT EXISTS customer_return_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    return_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty INT NOT NULL DEFAULT 1
);
