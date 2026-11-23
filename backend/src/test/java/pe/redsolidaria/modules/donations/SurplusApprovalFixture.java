package pe.redsolidaria.modules.donations;

import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.test.util.ReflectionTestUtils;
import pe.redsolidaria.modules.access.domain.*;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.*;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.*;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;
import pe.redsolidaria.modules.resources.domain.Resource;
import tools.jackson.databind.json.JsonMapper;

/** In-memory fixture only: no datasource, migrations, JDBC or persistent writes. */
final class SurplusApprovalFixture {
    final DonationRepository donations;
    final UserRepository users;
    final DonationMovementRepository movements;
    final AuditRecordRepository audit;
    final EntityManager em;
    final ReceiptOperationRepository operations;
    final JsonMapper json;
    final DonationReceiptService service;
    final String receiptUuid="ec455615-a53f-48d1-81ec-df9afc5bde1a";
    UserAccount actor;
    Donation donation;
    DonationLine line;
    ReceiptOperation operation;

    SurplusApprovalFixture() {
        this(mock(DonationRepository.class),mock(UserRepository.class),mock(DonationMovementRepository.class),
            mock(AuditRecordRepository.class),mock(EntityManager.class),mock(ReceiptOperationRepository.class),JsonMapper.builder().build());
    }
    SurplusApprovalFixture(DonationRepository donations,UserRepository users,DonationMovementRepository movements,
            AuditRecordRepository audit,EntityManager em,ReceiptOperationRepository operations,JsonMapper json) {
        this.donations=donations; this.users=users; this.movements=movements; this.audit=audit;
        this.em=em; this.operations=operations; this.json=json;
        service=new DonationReceiptService(donations,users,movements,audit,em,operations,json);
    }
    void prepare(boolean master,boolean own) {
        var sharedRole=new Role("ADMINISTRADOR","Administrador",null);
        sharedRole.asignar(new Permission("ACCESO_ADMINISTRAR",null));
        sharedRole.asignar(new Permission("DONACION_REGISTRAR",null));
        actor=new UserAccount(sharedRole,"Decisor","Prueba","actor@example.test","unused","ACTIVO");
        ReflectionTestUtils.setField(actor,"id",17L);
        if (master) ReflectionTestUtils.setField(actor,"adminMaestro",true);
        var registrar=own ? actor : new UserAccount(sharedRole,"Registrante","Prueba","registrar@example.test","unused","ACTIVO");
        if (!own) ReflectionTestUtils.setField(registrar,"id",29L);
        var center=mock(CollectionCenter.class); when(center.getId()).thenReturn(3L);
        donation=new Donation(mock(Donor.class),null,center,registrar,"RS-SURPLUS",null,"RECIBIDA",null);
        ReflectionTestUtils.setField(donation,"id",22L);
        var resource=mock(Resource.class); when(resource.getId()).thenReturn(8L); when(resource.getNombre()).thenReturn("Arroz");
        line=new DonationLine(donation,resource,q("10.000"));
        line.verificar(q("13.000"),q("10.000"),q("1.000"),q("2.000"),true);
        donation.getDetalles().add(line);
        var original=new RegisterReceiptResponse(22L,"RECIBIDA",LocalDateTime.of(2026,11,12,10,0),null,"Recepción original",
            List.of(new ReceiptLineResult(8L,"Arroz",q("10.000"),q("10.000"),q("0.000"),q("13.000"),q("1.000"),q("2.000"),true)),
            44L,3L,registrar.getId(),false);
        operation=new ReceiptOperation(donation,receiptUuid,"original-fingerprint",json.writeValueAsString(original));
        when(donations.findByIdForUpdate(22L)).thenReturn(Optional.of(donation));
        when(operations.findByDonacion_IdAndOperacionId(22L,receiptUuid)).thenReturn(Optional.of(operation));
        when(users.findByCorreo(actor.getCorreo())).thenReturn(Optional.of(actor));
    }
    DecideSurplusRequest approval() { return new DecideSurplusRequest(UUID.randomUUID(),true," Excedente apto comprobado "); }
    static BigDecimal q(String value) { return new BigDecimal(value); }
}
