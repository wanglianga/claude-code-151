package com.citylibrary.pll.web;

import com.citylibrary.pll.domain.Hold;
import com.citylibrary.pll.dto.Requests;
import com.citylibrary.pll.dto.Views;
import com.citylibrary.pll.service.HoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "预约排队", description = "读者预约、取消、排队与取书期限查询")
@RestController
@RequestMapping("/api")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @Operation(summary = "提交预约（自动锁码并生成调拨任务）")
    @PostMapping("/books/{bookId}/holds")
    public Views.HoldView place(@PathVariable Long bookId,
                                @Valid @RequestBody Requests.HoldRequest req,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        Hold hold = holdService.placeHold(bookId, req.cardNo(), req.pickupLibrary(), operator);
        return Views.of(holdService.get(hold.getId()));
    }

    @Operation(summary = "取消预约（锁码释放，自动补给下一位）")
    @PostMapping("/holds/{id}/cancel")
    public Views.HoldView cancel(@PathVariable Long id,
                                 @RequestBody(required = false) Requests.CancelRequest req,
                                 @RequestHeader(value = "X-Operator", required = false) String operator) {
        return Views.of(holdService.cancel(id, req == null ? null : req.reason(), operator));
    }

    @Operation(summary = "预约详情")
    @GetMapping("/holds/{id}")
    public Views.HoldView get(@PathVariable Long id) {
        return Views.of(holdService.get(id));
    }

    @Operation(summary = "读者的全部预约")
    @GetMapping("/patrons/{cardNo}/holds")
    public List<Views.HoldView> patronHolds(@PathVariable String cardNo) {
        return holdService.patronHolds(cardNo).stream().map(Views::of).toList();
    }
}
