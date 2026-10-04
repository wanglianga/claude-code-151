package com.citylibrary.pll.web;

import com.citylibrary.pll.dto.StatsDtos;
import com.citylibrary.pll.dto.Views;
import com.citylibrary.pll.service.AdminService;
import com.citylibrary.pll.service.ClosureService;
import com.citylibrary.pll.service.HoldService;
import com.citylibrary.pll.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端", description = "按馆/书目/读者/物流批次统计，闭馆管理，批处理补跑，审计留痕")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ClosureService closureService;
    private final HoldService holdService;
    private final LoanService loanService;

    public AdminController(AdminService adminService, ClosureService closureService,
                           HoldService holdService, LoanService loanService) {
        this.adminService = adminService;
        this.closureService = closureService;
        this.holdService = holdService;
        this.loanService = loanService;
    }

    @Operation(summary = "管理统计总览（馆/书目/读者/物流四视角一次返回）")
    @GetMapping("/stats")
    public StatsDtos.AdminBundle stats() {
        return adminService.bundle();
    }

    @Operation(summary = "最近 100 条审计日志（人工调整依据与影响）")
    @GetMapping("/audit")
    public List<Views.AuditView> audit() {
        return adminService.recentAudit().stream().map(Views::of).toList();
    }

    @Operation(summary = "某实体的审计轨迹")
    @GetMapping("/audit/{entityType}/{entityId}")
    public List<Views.AuditView> auditFor(@PathVariable String entityType, @PathVariable Long entityId) {
        return adminService.auditFor(entityType.toUpperCase(), entityId).stream().map(Views::of).toList();
    }

    @Operation(summary = "手动补跑：释放取书超时预约")
    @PostMapping("/jobs/expire-holds")
    public Map<String, Integer> expireHolds() {
        return Map.of("expired", holdService.expireReadyHolds());
    }

    @Operation(summary = "手动补跑：逾期罚款滚动与到期提醒")
    @PostMapping("/jobs/scan-overdue")
    public Map<String, Integer> scanOverdue() {
        return Map.of("touched", loanService.scanOverdueAndRemind());
    }

    @Operation(summary = "设置临时闭馆（body.reopenDate=YYYY-MM-DD），取书期限自动顺延")
    @PostMapping("/libraries/{code}/close")
    public Views.LibraryView close(@PathVariable String code,
                                   @RequestBody Map<String, String> body,
                                   @RequestHeader(value = "X-Operator", defaultValue = "ADMIN") String operator) {
        return Views.of(closureService.close(code, java.time.LocalDate.parse(body.get("reopenDate")), operator));
    }

    @Operation(summary = "恢复开放")
    @PostMapping("/libraries/{code}/reopen")
    public Views.LibraryView reopen(@PathVariable String code,
                                    @RequestHeader(value = "X-Operator", defaultValue = "ADMIN") String operator) {
        return Views.of(closureService.reopen(code, operator));
    }
}
