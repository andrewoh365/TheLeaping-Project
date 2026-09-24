import com.leaping.portfolio_app.entity.instrument;
import com.leaping.portfolio_app.service.InstrumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/instruments")
@CrossOrigin(origins = "*")
public class InstrumentController {
    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public ResponseEntity<List<instrument>> getAllInstruments() {
        List<instrument> instruments = instrumentService.getAllInstruments();
        return ResponseEntity.ok(instruments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<instrument> getInstrumentById(@PathVariable Long id) {
        return instrumentService.getInstrumentById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
