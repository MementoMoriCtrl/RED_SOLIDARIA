-- Nueva tabla: la identidad durable distingue un reintento de otra recepción legítima.
-- No modifica ni reescribe donaciones, movimientos ni auditorías anteriores.
CREATE TABLE recepcion_operacion (
    id_recepcion_operacion BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_donacion BIGINT UNSIGNED NOT NULL,
    operacion_id CHAR(36) NOT NULL,
    huella CHAR(64) NOT NULL,
    respuesta JSON NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_recepcion_operacion),
    UNIQUE KEY uk_recepcion_operacion (id_donacion, operacion_id),
    CONSTRAINT fk_recepcion_operacion_donacion FOREIGN KEY (id_donacion)
        REFERENCES donacion (id_donacion) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
