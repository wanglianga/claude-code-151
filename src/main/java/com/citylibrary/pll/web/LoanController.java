package com.citylibrary.pll.web;

import com.citylibrary.pll.domain.Loan;
import com.citylibrary.pll.dto.Requests;
import com.citylibrary.pll.dto.Views;
import com.citylibrary.pll.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "借阅与续借", description = "预约取书、现场借书、续借（次数/排队冲突/热门书校验）")
@RestController
@RequestMapping("/api")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @Operation(summary = "预约取书借出（扫读者预约 + 条码）")
    @PostMapping("/holds/{id}/checkout")
    public Views.LoanView checkoutHold(@PathVariable Long id,
                                       @Valid @RequestBody Requests.HoldCheckoutRequest req,
                                       @RequestHeader(value = "X-Operator", defaultValue = "STAFF") String operator) {
        return Views.of(loanService.checkoutHold(id, req.barcode(), operator));
    }

    @Operation(summary = "现场直接借书（本馆有排队时会被拒绝）")
    @PostMapping("/loans/checkout-direct")
    public Views.LoanView direct(@Valid @RequestBody Requests.DirectCheckoutRequest req,
                                 @RequestHeader(value = "X-Operator", defaultValue = "STAFF") String operator) {
        return Views.of(loanService.checkoutDirect(req.cardNo(), req.barcode(), req.library(), operator));
    }

    @Operation(summary = "续借（校验续借次数、排队冲突、热门书、逾期）")
    @PostMapping("/loans/{id}/renew")
    public Views.LoanView renew(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return Views.of(loanService.renew(id, operator));
    }

    @Operation(summary = "借阅详情")
    @GetMapping("/loans/{id}")
    public Views.LoanView get(@PathVariable Long id) {
        return Views.of(loanService.get(id));
    }

    @Operation(summary = "读者当前在借")
    @GetMapping("/patrons/{cardNo}/loans")
    public List<Views.LoanView> patronLoans(@PathVariable String cardNo) {
        return loanService.patronLoans(cardNo).stream().map(Views::of).toList();
    }
}
