package com.example.library.web;

import com.example.library.service.FineService;
import com.example.library.web.dto.FineAdjustRequest;
import com.example.library.web.dto.FineView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fines")
@Tag(name = "6. 罚款与赔偿", description = "读者账单、缴费、馆员调减/豁免（含规则依据与审计留痕）")
public class FineController {

    private final FineService fines;

    public FineController(FineService fines) {
        this.fines = fines;
    }

    @GetMapping("/readers/{cardNo}")
    @Operation(summary = "读者罚款/赔偿账单（含规则依据）")
    public List<FineView> forReader(@PathVariable String cardNo) {
        return fines.readerViews(cardNo);
    }

    @GetMapping("/{fineId}")
    @Operation(summary = "罚款单详情")
    public FineView get(@PathVariable Long fineId) {
        return fines.fineView(fineId);
    }

    @PostMapping("/{fineId}/payments")
    @Operation(summary = "读者缴纳罚款")
    public FineView pay(@PathVariable Long fineId) {
        return fines.payView(fineId);
    }

    @PostMapping("/{fineId}/adjustments")
    @Operation(summary = "馆员人工调减(REDUCE)/豁免(WAIVE)：必须填写理由，原值/新值/操作人/规则依据全部留痕")
    public FineView adjust(@PathVariable Long fineId, @Valid @RequestBody FineAdjustRequest req) {
        return fines.adjustView(fineId, req.staff(), req.action(), req.newAmount(), req.reason());
    }

    @GetMapping("/{fineId}/adjustments")
    @Operation(summary = "查看罚款单的人工调整历史")
    public List<Map<String, Object>> history(@PathVariable Long fineId) {
        return fines.adjustmentHistory(fineId).stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "staff", a.getStaff(),
                        "action", a.getAction(),
                        "oldAmount", a.getOldAmount(),
                        "newAmount", a.getNewAmount(),
                        "reason", a.getReason(),
                        "ruleBasis", a.getRuleBasis() == null ? "" : a.getRuleBasis(),
                        "createdAt", a.getCreatedAt()))
                .toList();
    }
}
