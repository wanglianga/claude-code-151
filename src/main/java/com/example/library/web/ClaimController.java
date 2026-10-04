package com.example.library.web;

import com.example.library.domain.Claim;
import com.example.library.service.LoanService;
import com.example.library.web.dto.ClaimRequest;
import com.example.library.web.dto.ClaimView;
import com.example.library.web.dto.ResolveClaimRequest;
import com.example.library.web.dto.StaffActionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
@Tag(name = "5. “声称已还”争议", description = "读者申报、馆员核查（找到/未找到/馆方豁免）与罚款联动")
public class ClaimController {

    private final LoanService loans;

    public ClaimController(LoanService loans) {
        this.loans = loans;
    }

    @PostMapping("/loans/{loanId}")
    @Operation(summary = "读者声称已还：开立核查工单，借阅进入争议状态，暂停逾期累计")
    public ClaimView claim(@PathVariable Long loanId, @RequestBody(required = false) ClaimRequest req) {
        return loans.claimView(loanId, req == null ? null : req.note());
    }

    @GetMapping("/open")
    @Operation(summary = "（馆员）待处理核查工单")
    public List<ClaimView> open() {
        return loans.openClaimViews();
    }

    @PostMapping("/{claimId}/found")
    @Operation(summary = "（馆员）在馆找到：正常入库并豁免读者逾期罚款")
    public ClaimView found(@PathVariable Long claimId, @org.springframework.web.bind.annotation.RequestBody ResolveClaimRequest req) {
        return loans.resolveFoundView(claimId, req.foundLibraryCode(), req.staff());
    }

    @PostMapping("/{claimId}/not-found")
    @Operation(summary = "（馆员）核查期满未找到：按丢书赔书价生成赔偿单")
    public ClaimView notFound(@PathVariable Long claimId, @org.springframework.web.bind.annotation.RequestBody StaffActionRequest req) {
        return loans.resolveNotFoundView(claimId, req.staff());
    }

    @PostMapping("/{claimId}/waive")
    @Operation(summary = "（馆员）馆方豁免：记录理由，图书入库流转，未缴罚款豁免并留痕")
    public ClaimView waive(@PathVariable Long claimId, @org.springframework.web.bind.annotation.RequestBody ResolveClaimRequest req) {
        return loans.waiveView(claimId, req.staff(), req.reason(), req.foundLibraryCode());
    }
}
