package pe.redsolidaria.modules.audit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.redsolidaria.modules.audit.domain.AuditRecord;

import java.time.LocalDateTime;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    @Query(value = """
            select a from AuditRecord a join fetch a.usuario u
            where (:accion is null or lower(a.accion) = lower(:accion))
              and (:entidad is null or lower(a.entidad) = lower(:entidad))
              and (:idRegistro is null or a.idRegistro = :idRegistro)
              and (:usuario is null or lower(u.correo) like lower(concat('%', :usuario, '%'))
                   or lower(concat(u.nombre, ' ', u.apellido)) like lower(concat('%', :usuario, '%')))
              and (:desde is null or a.fechaHora >= :desde)
              and (:hasta is null or a.fechaHora <= :hasta)
            """,
            countQuery = """
            select count(a) from AuditRecord a join a.usuario u
            where (:accion is null or lower(a.accion) = lower(:accion))
              and (:entidad is null or lower(a.entidad) = lower(:entidad))
              and (:idRegistro is null or a.idRegistro = :idRegistro)
              and (:usuario is null or lower(u.correo) like lower(concat('%', :usuario, '%'))
                   or lower(concat(u.nombre, ' ', u.apellido)) like lower(concat('%', :usuario, '%')))
              and (:desde is null or a.fechaHora >= :desde)
              and (:hasta is null or a.fechaHora <= :hasta)
            """)
    Page<AuditRecord> search(@Param("accion") String accion, @Param("entidad") String entidad,
            @Param("idRegistro") String idRegistro, @Param("usuario") String usuario,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, Pageable pageable);
}
