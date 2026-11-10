-- Preparada, NO aplicada. No convierte existencias ni reservas históricas.
CREATE TABLE centro_distribucion (
 id_centro_distribucion BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(150) NOT NULL,
 ubicacion VARCHAR(200) NULL,
 descripcion VARCHAR(2000) NULL,
 creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Una operación traslada un lote de una línea de donación, conservando su origen.
CREATE TABLE traslado_recurso (
 operacion_id CHAR(36) NOT NULL PRIMARY KEY,
 id_detalle_donacion BIGINT UNSIGNED NOT NULL,
 id_centro_acopio BIGINT UNSIGNED NOT NULL,
 id_centro_distribucion BIGINT UNSIGNED NOT NULL,
 id_unidad_medida BIGINT UNSIGNED NOT NULL,
 cantidad_enviada DECIMAL(14,3) NOT NULL,
 cantidad_recibida DECIMAL(14,3) NULL,
 id_usuario_salida BIGINT UNSIGNED NOT NULL,
 id_usuario_recepcion BIGINT UNSIGNED NULL,
 fecha_salida DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 fecha_recepcion DATETIME(6) NULL,
 estado VARCHAR(30) NOT NULL,
 observaciones VARCHAR(2000) NULL,
 observaciones_recepcion VARCHAR(2000) NULL,
 huella_salida CHAR(64) NOT NULL,
 operacion_recepcion CHAR(36) NULL,
 huella_recepcion CHAR(64) NULL,
 KEY idx_traslado_detalle (id_detalle_donacion),
 KEY idx_traslado_destino (id_centro_distribucion, fecha_salida),
 CONSTRAINT fk_traslado_detalle FOREIGN KEY (id_detalle_donacion) REFERENCES detalle_donacion(id_detalle_donacion),
 CONSTRAINT fk_traslado_origen FOREIGN KEY (id_centro_acopio) REFERENCES centro_acopio(id_centro_acopio),
 CONSTRAINT fk_traslado_destino FOREIGN KEY (id_centro_distribucion) REFERENCES centro_distribucion(id_centro_distribucion),
 CONSTRAINT fk_traslado_unidad FOREIGN KEY (id_unidad_medida) REFERENCES unidad_medida(id_unidad_medida),
 CONSTRAINT fk_traslado_salida FOREIGN KEY (id_usuario_salida) REFERENCES usuario(id_usuario),
 CONSTRAINT fk_traslado_recepcion FOREIGN KEY (id_usuario_recepcion) REFERENCES usuario(id_usuario),
 CONSTRAINT chk_traslado_cantidades CHECK (cantidad_enviada > 0 AND (cantidad_recibida IS NULL OR (cantidad_recibida >= 0 AND cantidad_recibida <= cantidad_enviada))),
 CONSTRAINT chk_traslado_estado CHECK (
 (estado = 'EN_TRANSITO' AND cantidad_recibida IS NULL AND fecha_recepcion IS NULL AND operacion_recepcion IS NULL AND id_usuario_recepcion IS NULL AND huella_recepcion IS NULL)
 OR (estado IN ('RECIBIDO','RECIBIDO_CON_DIFERENCIA') AND cantidad_recibida IS NOT NULL AND fecha_recepcion IS NOT NULL AND operacion_recepcion IS NOT NULL AND id_usuario_recepcion IS NOT NULL AND huella_recepcion IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Identifica reservas previas sin inventar una recepción en distribución.
ALTER TABLE detalle_entrega ADD COLUMN origen_distribucion BOOLEAN NOT NULL DEFAULT FALSE;

-- Evita reinterpretar cantidades históricas incluso en actualizaciones concurrentes.
DELIMITER $$
CREATE TRIGGER proteger_unidad_recurso BEFORE UPDATE ON recurso FOR EACH ROW
BEGIN
 IF OLD.id_unidad_medida <> NEW.id_unidad_medida AND
   (EXISTS(SELECT 1 FROM detalle_donacion WHERE id_recurso=OLD.id_recurso) OR
    EXISTS(SELECT 1 FROM necesidad WHERE id_recurso=OLD.id_recurso)) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No se puede cambiar la unidad de un recurso con historia';
 END IF;
END$$
DELIMITER ;
