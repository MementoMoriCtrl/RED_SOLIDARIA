CREATE TABLE resolucion_traslado (
 operacion_id CHAR(36) NOT NULL PRIMARY KEY,
 traslado_id CHAR(36) NOT NULL,
 id_usuario BIGINT UNSIGNED NOT NULL,
 fecha_resolucion DATETIME(6) NOT NULL,
 tipo VARCHAR(40) NOT NULL,
 motivo VARCHAR(2000) NOT NULL,
 evidencia VARCHAR(2000) NULL,
 cantidad_enviada DECIMAL(14,3) NOT NULL,
 cantidad_recibida DECIMAL(14,3) NOT NULL,
 diferencia DECIMAL(14,3) NOT NULL,
 CONSTRAINT uq_resolucion_traslado UNIQUE (traslado_id),
 CONSTRAINT fk_resolucion_traslado FOREIGN KEY (traslado_id) REFERENCES traslado_recurso(operacion_id),
 CONSTRAINT fk_resolucion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
 CONSTRAINT chk_resolucion_diferencia CHECK (cantidad_enviada > cantidad_recibida AND cantidad_recibida >= 0 AND diferencia = cantidad_enviada - cantidad_recibida)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
