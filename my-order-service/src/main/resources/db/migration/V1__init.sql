-- Initial schema for order-service, applied by Flyway on start-up.
-- Never edit a migration that has already run: Flyway stores a checksum of each file and
-- refuses to start if it changes. Add a new V2__... file instead.
CREATE TABLE IF NOT EXISTS `t_orders`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT,
    `order_number` varchar(255) DEFAULT NULL,
    `sku_code`     varchar(255),
    `price`        decimal(19, 2),
    `quantity`     int,
    PRIMARY KEY (`id`)
);
