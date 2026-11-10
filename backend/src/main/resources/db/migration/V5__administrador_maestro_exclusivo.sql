-- PREPARADA, NO APLICADA. Revisar y autorizar contra el destino antes de ejecutar.
-- ADMIN_MAESTRO es una autoridad de cuenta, nunca una asignacion rol_permiso.
-- El correo solo comprueba la identidad de despliegue; no autoriza en runtime.
DELIMITER $$
CREATE PROCEDURE validar_destino_maestro_v5()
BEGIN
    IF (SELECT COUNT(*) FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
        WHERE u.id_usuario = 3 AND u.correo = 'nsm_26k@hotmail.com'
          AND u.id_rol = 4 AND r.codigo = 'ADMINISTRADOR' AND u.estado = 'ACTIVO') <> 1
       OR (SELECT COUNT(DISTINCT p.codigo) FROM rol_permiso rp
           JOIN permiso p ON p.id_permiso = rp.id_permiso
           WHERE rp.id_rol = 4 AND p.codigo IN ('ACCESO_ADMINISTRAR','DONACION_REGISTRAR')) <> 2
       OR EXISTS (SELECT 1 FROM permiso WHERE UPPER(TRIM(codigo)) = 'ADMIN_MAESTRO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V5: identidad, permisos o permiso reservado no coinciden; revisar destino';
    END IF;
END$$
DELIMITER ;
CALL validar_destino_maestro_v5();
DROP PROCEDURE validar_destino_maestro_v5;

ALTER TABLE usuario
    ADD COLUMN admin_maestro TINYINT NULL DEFAULT NULL,
    ADD CONSTRAINT chk_usuario_admin_maestro CHECK (admin_maestro IS NULL OR admin_maestro = 1),
    ADD CONSTRAINT uk_usuario_admin_maestro UNIQUE (admin_maestro);

-- UNIQUE permite muchos NULL pero solo un 1. No se altera el rol compartido.
UPDATE usuario SET admin_maestro = 1
WHERE id_usuario = 3 AND correo = 'nsm_26k@hotmail.com' AND id_rol = 4 AND estado = 'ACTIVO';
-- MySQL DDL tiene commits implicitos. Un fallo requiere inspeccion antes de reintentar.
