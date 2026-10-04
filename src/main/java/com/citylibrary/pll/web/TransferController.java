package com.citylibrary.pll.web;

import com.citylibrary.pll.domain.Transfer;
import com.citylibrary.pll.dto.Requests;
import com.citylibrary.pll.dto.Views;
import com.citylibrary.pll.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "馆际调拨", description = "物流员取书出库、到馆登记、丢包处理")
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @Operation(summary = "全部调拨任务")
    @GetMapping
    public List<Views.TransferView> all() {
        return transferService.all().stream().map(Views::of).toList();
    }

    @Operation(summary = "调拨详情（含出库馆/目的馆/包裹号/ETA）")
    @GetMapping("/{id}")
    public Views.TransferView get(@PathVariable Long id) {
        return Views.of(transferService.get(id));
    }

    @Operation(summary = "物流员取书出库：登记包裹编号")
    @PostMapping("/{id}/pickup")
    public Views.TransferView pickup(@PathVariable Long id,
                                     @Valid @RequestBody Requests.PickupRequest req,
                                     @RequestHeader(value = "X-Operator", defaultValue = "COURIER") String operator) {
        return Views.of(transferService.pickup(id, req.packageNo(), operator));
    }

    @Operation(summary = "到馆登记（自动通知取书/失效预约回架补给）")
    @PostMapping("/{id}/arrive")
    public Views.TransferView arrive(@PathVariable Long id,
                                     @RequestHeader(value = "X-Operator", defaultValue = "STAFF") String operator) {
        return Views.of(transferService.arrive(id, operator));
    }

    @Operation(summary = "登记物流丢包（馆方承担，读者免责并自动重分配）")
    @PostMapping("/{id}/lost")
    public Views.IncidentView lost(@PathVariable Long id,
                                   @Valid @RequestBody Requests.LostRequest req,
                                   @RequestHeader(value = "X-Operator", defaultValue = "COURIER") String operator) {
        return Views.of(transferService.reportLost(id, req.note(), operator));
    }
}
