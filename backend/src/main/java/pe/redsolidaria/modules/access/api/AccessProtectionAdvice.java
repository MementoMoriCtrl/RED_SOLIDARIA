package pe.redsolidaria.modules.access.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ControllerAdvice(assignableTypes=AccessManagementController.class)
public class AccessProtectionAdvice extends RequestBodyAdviceAdapter {
    private final JsonMapper json;
    private final ObjectProvider<UserRepository> users;
    private final ObjectProvider<AuditRecordRepository> audit;
    public AccessProtectionAdvice(JsonMapper json,ObjectProvider<UserRepository> users,ObjectProvider<AuditRecordRepository> audit) {
        this.json=json; this.users=users; this.audit=audit;
    }
    @Override
    public boolean supports(MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) {
        return parameter.getContainingClass()==AccessManagementController.class;
    }
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter parameter,Type type,
            Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        JsonNode body;
        try { body=json.readTree(input.getBody()); }
        catch (RuntimeException invalid) { throw new HttpMessageNotReadableException("El cuerpo debe ser un JSON válido",invalid,input); }
        if (body!=null && (body.has("admin_maestro") || body.has("adminMaestro"))) throw new ReservedFlagException();
        byte[] bytes=json.writeValueAsBytes(body);
        var headers=new HttpHeaders(); headers.putAll(input.getHeaders()); headers.setContentLength(bytes.length);
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
            @Override public HttpHeaders getHeaders() { return headers; }
        };
    }
    // MVC handles this after the transactional service has rolled back and released its account lock.
    // The independent transaction prevents the rejection audit from being rolled back with the denied operation.
    @ExceptionHandler(ResponseStatusException.class)
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ResponseEntity<ProblemDetail> rejected(ResponseStatusException failure,HttpServletRequest request) {
        int status=failure.getStatusCode().value();
        if ((status==403 || status==409 || failure instanceof ReservedFlagException) && request.getUserPrincipal()!=null) {
            var actor=users.getObject().findByCorreo(request.getUserPrincipal().getName().trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
            String path=request.getRequestURI();
            audit.getObject().saveAndFlush(new AuditRecord(actor,"ACCESO",path.substring(0,Math.min(path.length(),100)),
                "OPERACION_ACCESO_BLOQUEADA",null,json.writeValueAsString(Map.of(
                    "metodo",request.getMethod(),"ruta",path,"estadoHttp",status,
                    "motivo",failure.getReason()==null ? "Operación no permitida" : failure.getReason()))));
        }
        return ResponseEntity.status(failure.getStatusCode()).body(ProblemDetail.forStatusAndDetail(failure.getStatusCode(),
            failure.getReason()==null ? "Operación no permitida" : failure.getReason()));
    }
    private static final class ReservedFlagException extends ResponseStatusException {
        ReservedFlagException() { super(HttpStatus.BAD_REQUEST,"admin_maestro es un atributo reservado y no puede enviarse a esta API"); }
    }
}
