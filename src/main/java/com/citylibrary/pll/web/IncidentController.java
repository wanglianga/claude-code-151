package com.citylibrary.pll.web;

import com.citylibrary.pll.dto.Requests;
import com.citylibrary.pll.dto.Views;
import com.citylibrary.pll.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "异常处理", description = "声称已还争议、读者遗失、丢包后找回等异常流程")
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @Operation(summary = "全部异常单")
    @GetMapping
    public List<Views.IncidentView> all() {
        return incidentService.all().stream().map(Views::of).toList();
    }

    @Operation(summary = "未结异常单")
    @GetMapping("/open")
    public List<Views.IncidentView> open() {
        return incidentService.openIncidents().stream().map(Views::of).toList();
    }

    @Operation(summary = "读者声称已还但系统未入库")
    @PostMapping("/loans/{loanId}/return-claim")
    public Views.IncidentView claim(@PathVariable Long loanId,
                                    @Valid @RequestBody Requests.ClaimRequest req,
                                    @RequestHeader(value = "X-Operator", required = false) String operator) {
        return Views.of(incidentService.fileReturnClaim(loanId, req.description(), operator));
    }

    @Operation(summary = "争议核查：馆内找到，按申报日核销")
    @PostMapping("/{id}/resolve-found")
    public Views.IncidentView found(@PathVariable Long id,
                                    @Valid @RequestBody Requests.ResolveFoundRequest req,
                                    @RequestHeader(value = "X-Operator", defaultValue = "STAFF") String operator) {
        return Views.of(incidentService.resolveClaimFound(id, req.foundLibrary(), operator));
    }

    @Operation(summary = "争议核查：确认遗失，开赔偿单")
    @PostMapping("/{id}/resolve-lost")
    public Views.IncidentView lost(@PathVariable Long id,
                                   @RequestHeader(value = "X-Operator", defaultValue = "STAFF") String operator) {
        return Views.of(incidentService.resolveClaimLost(id, operator));
    }

    @Operation(summary = "读者直接申报遗失")
    @PostMapping("/loans/{loanId}/reader-lost")
    public Views.FineView readerLost(@PathVariable Long loanId,
                                     @Valid @RequestBody Requests.ClaimRequest req,
                                     @RequestHeader(value = "X-Operator", required = false) String operator) {
        return Views.of(incidentService.reportReaderLost(loanId, req.description(), operator));
    }
}
