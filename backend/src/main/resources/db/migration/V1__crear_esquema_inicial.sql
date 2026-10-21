-- Red Solidaria: esquema relacional inicial.
-- No contiene datos semilla ni credenciales.
--
-- Normalización de estados documentada:
--   "En tránsito" y "En transporte" se almacenan como EN_TRANSITO.
--   Se conservan además REGISTRADA, RECIBIDA, ALMACENADA, CLASIFICADA,
--   ENTREGADA, DEVUELTA e INCIDENCIA. Esta migración no define transiciones.
--
-- Las cantidades recibidas en acopio se guardan por línea de donación.
-- cantidad_asignada y cantidad_entregada en detalle_entrega son incrementos
-- de eventos; los acumulados se consultan sumando las líneas, no se duplican.
-- Los límites entre filas (asignado <= recibido y entregado <= asignado
-- acumulado) deben validarse en una transacción de servicio con bloqueo.

CREATE TABLE rol (
    id_rol BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(50) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_rol),
    UNIQUE KEY uk_rol_codigo (codigo),
    KEY idx_rol_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permiso (
    id_permiso BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(80) NOT NULL,
    descripcion VARCHAR(255) NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_permiso),
    UNIQUE KEY uk_permiso_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rol_permiso (
    id_rol BIGINT UNSIGNED NOT NULL,
    id_permiso BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id_rol, id_permiso),
    KEY idx_rol_permiso_permiso (id_permiso),
    CONSTRAINT fk_rol_permiso_rol
        FOREIGN KEY (id_rol) REFERENCES rol (id_rol)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_rol_permiso_permiso
        FOREIGN KEY (id_permiso) REFERENCES permiso (id_permiso)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE usuario (
    id_usuario BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_rol BIGINT UNSIGNED NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    correo VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_usuario),
    UNIQUE KEY uk_usuario_correo (correo),
    KEY idx_usuario_rol (id_rol),
    KEY idx_usuario_estado (estado),
    CONSTRAINT fk_usuario_rol
        FOREIGN KEY (id_rol) REFERENCES rol (id_rol)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE emergencia (
    id_emergencia BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(150) NOT NULL,
    tipo VARCHAR(100) NOT NULL,
    descripcion TEXT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NULL,
    estado VARCHAR(30) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_emergencia),
    KEY idx_emergencia_estado_inicio (estado, fecha_inicio),
    CONSTRAINT chk_emergencia_fechas
        CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE zona_afectada (
    id_zona_afectada BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_emergencia BIGINT UNSIGNED NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    ubicacion VARCHAR(200) NULL,
    descripcion TEXT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_zona_afectada),
    KEY idx_zona_emergencia_nombre (id_emergencia, nombre),
    CONSTRAINT fk_zona_emergencia
        FOREIGN KEY (id_emergencia) REFERENCES emergencia (id_emergencia)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE centro_acopio (
    id_centro_acopio BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(150) NOT NULL,
    ubicacion VARCHAR(200) NULL,
    descripcion TEXT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_centro_acopio),
    KEY idx_centro_acopio_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE unidad_medida (
    id_unidad_medida BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    simbolo VARCHAR(20) NULL,
    PRIMARY KEY (id_unidad_medida),
    UNIQUE KEY uk_unidad_medida_codigo (codigo),
    KEY idx_unidad_medida_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE recurso (
    id_recurso BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_unidad_medida BIGINT UNSIGNED NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    descripcion TEXT NULL,
    estado VARCHAR(30) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_recurso),
    KEY idx_recurso_nombre (nombre),
    KEY idx_recurso_categoria_nombre (categoria, nombre),
    KEY idx_recurso_unidad (id_unidad_medida),
    CONSTRAINT fk_recurso_unidad_medida
        FOREIGN KEY (id_unidad_medida) REFERENCES unidad_medida (id_unidad_medida)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE donante (
    id_donante BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(150) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_donante),
    KEY idx_donante_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE donacion (
    id_donacion BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_donante BIGINT UNSIGNED NOT NULL,
    id_emergencia BIGINT UNSIGNED NULL,
    id_centro_acopio BIGINT UNSIGNED NULL,
    id_usuario_registro BIGINT UNSIGNED NOT NULL,
    codigo_trazabilidad VARCHAR(100) NOT NULL,
    fecha_registro DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    fecha_ingreso DATETIME(6) NULL,
    estado VARCHAR(30) NOT NULL,
    observaciones TEXT NULL,
    PRIMARY KEY (id_donacion),
    UNIQUE KEY uk_donacion_codigo_trazabilidad (codigo_trazabilidad),
    KEY idx_donacion_emergencia_estado_fecha (id_emergencia, estado, fecha_ingreso),
    KEY idx_donacion_centro_fecha (id_centro_acopio, fecha_ingreso),
    KEY idx_donacion_donante_fecha (id_donante, fecha_registro),
    KEY idx_donacion_usuario_registro (id_usuario_registro),
    CONSTRAINT chk_donacion_destino
        CHECK (id_emergencia IS NOT NULL OR id_centro_acopio IS NOT NULL),
    CONSTRAINT chk_donacion_estado CHECK (estado IN (
        'REGISTRADA', 'RECIBIDA', 'ALMACENADA', 'CLASIFICADA',
        'EN_TRANSITO', 'ENTREGADA', 'DEVUELTA', 'INCIDENCIA'
    )),
    CONSTRAINT fk_donacion_donante
        FOREIGN KEY (id_donante) REFERENCES donante (id_donante)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_donacion_emergencia
        FOREIGN KEY (id_emergencia) REFERENCES emergencia (id_emergencia)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_donacion_centro_acopio
        FOREIGN KEY (id_centro_acopio) REFERENCES centro_acopio (id_centro_acopio)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_donacion_usuario_registro
        FOREIGN KEY (id_usuario_registro) REFERENCES usuario (id_usuario)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE detalle_donacion (
    id_detalle_donacion BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_donacion BIGINT UNSIGNED NOT NULL,
    id_recurso BIGINT UNSIGNED NOT NULL,
    cantidad_donada DECIMAL(14,3) NOT NULL,
    cantidad_recibida_acopio DECIMAL(14,3) NOT NULL DEFAULT 0.000,
    PRIMARY KEY (id_detalle_donacion),
    UNIQUE KEY uk_detalle_donacion_recurso (id_donacion, id_recurso),
    KEY idx_detalle_donacion_recurso (id_recurso),
    CONSTRAINT chk_detalle_donacion_cantidades CHECK (
        cantidad_donada > 0 AND
        cantidad_recibida_acopio >= 0 AND
        cantidad_recibida_acopio <= cantidad_donada
    ),
    CONSTRAINT fk_detalle_donacion_donacion
        FOREIGN KEY (id_donacion) REFERENCES donacion (id_donacion)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_detalle_donacion_recurso
        FOREIGN KEY (id_recurso) REFERENCES recurso (id_recurso)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE necesidad (
    id_necesidad BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_zona_afectada BIGINT UNSIGNED NOT NULL,
    id_recurso BIGINT UNSIGNED NOT NULL,
    cantidad_requerida DECIMAL(14,3) NOT NULL,
    prioridad VARCHAR(30) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    fecha_registro DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    observaciones TEXT NULL,
    PRIMARY KEY (id_necesidad),
    KEY idx_necesidad_zona_estado_prioridad (id_zona_afectada, estado, prioridad),
    KEY idx_necesidad_recurso (id_recurso),
    CONSTRAINT chk_necesidad_cantidad CHECK (cantidad_requerida > 0),
    CONSTRAINT fk_necesidad_zona
        FOREIGN KEY (id_zona_afectada) REFERENCES zona_afectada (id_zona_afectada)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_necesidad_recurso
        FOREIGN KEY (id_recurso) REFERENCES recurso (id_recurso)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE entrega (
    id_entrega BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_usuario_responsable BIGINT UNSIGNED NOT NULL,
    fecha_hora DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    observaciones TEXT NULL,
    PRIMARY KEY (id_entrega),
    KEY idx_entrega_fecha (fecha_hora),
    KEY idx_entrega_usuario_fecha (id_usuario_responsable, fecha_hora),
    CONSTRAINT fk_entrega_usuario_responsable
        FOREIGN KEY (id_usuario_responsable) REFERENCES usuario (id_usuario)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE detalle_entrega (
    id_detalle_entrega BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_entrega BIGINT UNSIGNED NOT NULL,
    id_detalle_donacion BIGINT UNSIGNED NOT NULL,
    id_necesidad BIGINT UNSIGNED NOT NULL,
    cantidad_asignada DECIMAL(14,3) NOT NULL DEFAULT 0.000,
    cantidad_entregada DECIMAL(14,3) NOT NULL DEFAULT 0.000,
    PRIMARY KEY (id_detalle_entrega),
    KEY idx_detalle_entrega_entrega (id_entrega),
    KEY idx_detalle_entrega_donacion (id_detalle_donacion),
    KEY idx_detalle_entrega_necesidad (id_necesidad),
    CONSTRAINT chk_detalle_entrega_cantidades CHECK (
        cantidad_asignada >= 0 AND
        cantidad_entregada >= 0 AND
        (cantidad_asignada > 0 OR cantidad_entregada > 0)
    ),
    CONSTRAINT fk_detalle_entrega_entrega
        FOREIGN KEY (id_entrega) REFERENCES entrega (id_entrega)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_detalle_entrega_detalle_donacion
        FOREIGN KEY (id_detalle_donacion) REFERENCES detalle_donacion (id_detalle_donacion)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_detalle_entrega_necesidad
        FOREIGN KEY (id_necesidad) REFERENCES necesidad (id_necesidad)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE movimiento_donacion (
    id_movimiento BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_donacion BIGINT UNSIGNED NOT NULL,
    id_usuario_responsable BIGINT UNSIGNED NOT NULL,
    estado_anterior VARCHAR(30) NULL,
    estado_nuevo VARCHAR(30) NULL,
    fecha_hora DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ubicacion VARCHAR(200) NULL,
    observaciones TEXT NULL,
    PRIMARY KEY (id_movimiento),
    KEY idx_movimiento_donacion_fecha (id_donacion, fecha_hora),
    KEY idx_movimiento_usuario_fecha (id_usuario_responsable, fecha_hora),
    CONSTRAINT chk_movimiento_estado_anterior CHECK (
        estado_anterior IS NULL OR estado_anterior IN (
            'REGISTRADA', 'RECIBIDA', 'ALMACENADA', 'CLASIFICADA',
            'EN_TRANSITO', 'ENTREGADA', 'DEVUELTA', 'INCIDENCIA'
        )
    ),
    CONSTRAINT chk_movimiento_estado_nuevo CHECK (
        estado_nuevo IS NULL OR estado_nuevo IN (
            'REGISTRADA', 'RECIBIDA', 'ALMACENADA', 'CLASIFICADA',
            'EN_TRANSITO', 'ENTREGADA', 'DEVUELTA', 'INCIDENCIA'
        )
    ),
    CONSTRAINT fk_movimiento_donacion
        FOREIGN KEY (id_donacion) REFERENCES donacion (id_donacion)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_movimiento_usuario_responsable
        FOREIGN KEY (id_usuario_responsable) REFERENCES usuario (id_usuario)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE registro_auditoria (
    id_registro_auditoria BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_usuario BIGINT UNSIGNED NOT NULL,
    entidad VARCHAR(100) NOT NULL,
    id_registro VARCHAR(100) NOT NULL,
    accion VARCHAR(50) NOT NULL,
    fecha_hora DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    datos_anteriores JSON NULL,
    datos_nuevos JSON NULL,
    PRIMARY KEY (id_registro_auditoria),
    KEY idx_auditoria_entidad_registro_fecha (entidad, id_registro, fecha_hora),
    KEY idx_auditoria_usuario_fecha (id_usuario, fecha_hora),
    CONSTRAINT fk_auditoria_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
