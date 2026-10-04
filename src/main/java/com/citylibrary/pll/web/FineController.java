package com.citylibrary.pll.web;

import com.citylibrary.pll.domain.Fine;
import com.citylibrary.pll.dto.Requests;
import com.citylibrary.pll.dto.Views;
import com.citylibrary.pll.service.FineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "罚款与赔偿", description = "逾期/污损/遗失罚款查询、缴纳、馆员减免与人工调整")
@RestController
@RequestMapping("/api/fines")
public class FineController {

    private final FineService fineService;

    public FineController(FineService fineService) {
        this.fineService = fineService;
    }

    @Operation(summary = "罚款详情（含规则依据 ruleRef）")
    @GetMapping("/{id}")
    public Views.FineView get(@PathVariable Long id) {
        return Views.of(fineService.get(id));
    }

    @Operation(summary = "读者罚款列表")
    @GetMapping("/patron/{patronId}")
    public List<Views.FineView> byPatron(@PathVariable Long patronId) {
        return fineService.patronFines(patronId).stream().map(Views::of).toList();
    }

    @Operation(summary = "缴纳罚款")
    @PostMapping("/{id}/pay")
    public Views.FineView pay(@PathVariable Long id,
                              @RequestHeader(value = "X-Operator", defaultValue = "STAFF") String operator) {
        return Views.of(fineService.pay(id, operator));
    }

    @Operation(summary = "馆员减免（须填原因，留痕）")
    @PostMapping("/{id}/waive")
    public Views.FineView waive(@PathVariable Long id,
                                @Valid @RequestBody Requests.WaiveRequest req,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return Views.of(fineService.waive(id, req.reason(), operator));
    }

    @Operation(summary = "馆员调整金额（只能下调，须填原因）")
    @PostMapping("/{id}/adjust")
    public Views.FineView adjust(@PathVariable Long id,
                                 @Valid @RequestBody Requests.AdjustRequest req,
                                 @RequestHeader(value = "X-Operator", required = false) String operator) {
        return Views.of(fineService.adjust(id, req.newAmount(), req.reason(), operator));
    }
}
