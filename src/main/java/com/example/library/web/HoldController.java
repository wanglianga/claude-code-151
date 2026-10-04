package com.example.library.web;

import com.example.library.service.HoldService;
import com.example.library.web.dto.HoldRequest;
import com.example.library.web.dto.HoldView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holds")
@Tag(name = "2. 预约排队", description = "FIFO 队列、锁条码、超时释放、读者取消")
public class HoldController {

    private final HoldService holds;

    public HoldController(HoldService holds) {
        this.holds = holds;
    }

    @PostMapping("/bibs/{bibId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "读者预约某书目并指定取书馆；本馆有书立即上架待取，外馆有书则锁定条码生成调拨，否则排队")
    public HoldView place(@PathVariable Long bibId, @Valid @RequestBody HoldRequest req) {
        return holds.placeHoldView(bibId, req.readerCardNo(), req.pickupLibraryCode());
    }

    @PostMapping("/{holdId}/cancel")
    @Operation(summary = "读者取消预约；未出库解锁副本，在途则到馆后释放给下一位，已到架则直接再分配")
    public HoldView cancel(@PathVariable Long holdId) {
        return holds.cancelHoldView(holdId);
    }

    @GetMapping("/{holdId}")
    @Operation(summary = "查看预约详情（队列位置/锁定条码/取书时限）")
    public HoldView get(@PathVariable Long holdId) {
        return holds.holdView(holdId);
    }

    @GetMapping("/readers/{cardNo}")
    @Operation(summary = "查看读者有效预约")
    public List<HoldView> readerHolds(@PathVariable String cardNo) {
        return holds.activeHoldViewsForReader(cardNo);
    }

    @GetMapping("/active")
    @Operation(summary = "（馆员）查看全部活跃预约队列")
    public List<HoldView> allActive() {
        return holds.allActiveHoldViews();
    }

    @PostMapping("/jobs/expire")
    @Operation(summary = "（馆员/定时任务手动触发）扫描到馆超时未取的预约并释放给下一位")
    public int expireJob() {
        return holds.expireOverdueHolds();
    }
}
