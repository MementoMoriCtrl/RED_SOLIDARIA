package pe.redsolidaria.modules.logistics;

import static pe.redsolidaria.modules.logistics.LogisticsDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/logistica")
@PreAuthorize("hasAuthority('ACCESO_ADMINISTRAR')")
public class LogisticsController {
    private final LogisticsService service;
    public LogisticsController(LogisticsService service) { this.service=service; }
    @GetMapping("/centros-distribucion") public List<Center> centers() { return service.centers(); }
    @PostMapping("/centros-distribucion") public void create(@Valid @RequestBody SaveCenter request,Authentication actor) { service.saveCenter(null,request,actor.getName()); }
    @PutMapping("/centros-distribucion/{id}") public void update(@PathVariable long id,@Valid @RequestBody SaveCenter request,Authentication actor) { service.saveCenter(id,request,actor.getName()); }
    @GetMapping("/existencias") public List<Stock> stock(@RequestParam(defaultValue="0") @Min(0) int page) { return service.stock(page); }
    @GetMapping("/traslados") public List<Transfer> history(@RequestParam(defaultValue="0") @Min(0) int page) { return service.history(page); }
    @PostMapping("/traslados") public Transfer dispatch(@Valid @RequestBody Dispatch request,Authentication actor) { return service.dispatch(request,actor.getName()); }
    @PostMapping("/traslados/{id}/recepcion") public Transfer receive(@PathVariable String id,@Valid @RequestBody Receive request,Authentication actor) { return service.receive(id,request,actor.getName()); }
}
