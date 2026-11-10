-- PREPARADA, NO APLICADA. Conserva declaraciones, operaciones y cantidades históricas.
-- El acumulado aceptado puede superar lo declarado SOLO mediante autorización del servicio.
ALTER TABLE detalle_donacion DROP CHECK chk_detalle_donacion_cantidades,
 ADD CONSTRAINT chk_detalle_donacion_cantidades CHECK (cantidad_donada > 0 AND cantidad_recibida_acopio >= 0),
 ADD COLUMN cantidad_examinada DECIMAL(14,3) NOT NULL DEFAULT 0,
 ADD COLUMN cantidad_no_aceptada DECIMAL(14,3) NOT NULL DEFAULT 0,
 ADD COLUMN cantidad_excedente_pendiente DECIMAL(14,3) NOT NULL DEFAULT 0,
 ADD COLUMN cantidad_excedente_aprobada DECIMAL(14,3) NOT NULL DEFAULT 0,
 ADD COLUMN verificacion_cerrada BOOLEAN NOT NULL DEFAULT FALSE,
 ADD CONSTRAINT chk_verificacion_cantidades CHECK (cantidad_examinada >= 0 AND cantidad_no_aceptada >= 0
 AND cantidad_excedente_pendiente >= 0 AND cantidad_excedente_aprobada >= 0);
-- respuesta original sigue siendo inmutable; la decisión tiene identidad/respuesta propias.
ALTER TABLE recepcion_operacion
 ADD COLUMN decision VARCHAR(20) NULL,
 ADD COLUMN id_usuario_decision BIGINT UNSIGNED NULL,
 ADD COLUMN fecha_decision DATETIME(6) NULL,
 ADD COLUMN observaciones_decision VARCHAR(2000) NULL,
 ADD COLUMN uuid_decision CHAR(36) NULL,
 ADD COLUMN huella_decision CHAR(64) NULL,
 ADD COLUMN respuesta_decision JSON NULL,
 ADD CONSTRAINT fk_recepcion_decisor FOREIGN KEY (id_usuario_decision) REFERENCES usuario(id_usuario),
 ADD CONSTRAINT chk_recepcion_decision CHECK (decision IS NULL OR decision IN ('APROBADO','RECHAZADO'));
ALTER TABLE movimiento_donacion ADD COLUMN tipo_evento VARCHAR(50) NULL;
