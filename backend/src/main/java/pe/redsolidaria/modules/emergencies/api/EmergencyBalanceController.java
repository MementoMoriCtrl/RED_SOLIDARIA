package pe.redsolidaria.modules.emergencies.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyBalanceRepository;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;
import pe.redsolidaria.modules.emergencies.api.dto.EmergencyBalanceDtos.ResourceBalance;

@RestController
@RequestMapping("/api/admin/emergencias/{id}/balance")
public class EmergencyBalanceController {
    private final EmergencyRepository emergencies;
    private final EmergencyBalanceRepository balances;
    public EmergencyBalanceController(EmergencyRepository emergencies, EmergencyBalanceRepository balances) {
        this.emergencies = emergencies; this.balances = balances;
    }
    @GetMapping @Transactional(readOnly = true)
    public List<ResourceBalance> balance(@PathVariable long id) {
        if (!emergencies.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergencia no encontrada");
        return balances.balance(id);
    }
}
