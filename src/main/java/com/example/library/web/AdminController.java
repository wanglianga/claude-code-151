package com.example.library.web;

import com.example.library.service.AdminStatsService;
import com.example.library.web.dto.admin.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "10. 管理端统计", description = "按馆/书目/读者/物流批次查看调拨效率、热门预约、逾期与丢损")
public class AdminController {

    private final AdminStatsService stats;

    public AdminController(AdminStatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "总览：调拨量/到馆率/丢包/在借逾期/罚款分类/未缴金额")
    public OverallDashboard dashboard(@RequestParam(defaultValue = "30") int days) {
        return stats.dashboard(days);
    }

    @GetMapping("/libraries")
    @Operation(summary = "按馆统计：在架/在途/丢包/待取/逾期，平均出库到馆时长")
    public List<LibraryStats> libraries(@RequestParam(defaultValue = "30") int days) {
        return stats.libraryStats(days);
    }

    @GetMapping("/libraries/{code}")
    @Operation(summary = "单个分馆统计")
    public LibraryStats library(@PathVariable String code,
                                @RequestParam(defaultValue = "30") int days) {
        return stats.singleLibrary(code, days);
    }

    @GetMapping("/bibs/popular")
    @Operation(summary = "热门预约排行")
    public List<PopularBibRow> popular(@RequestParam(defaultValue = "30") int days,
                                       @RequestParam(defaultValue = "10") int limit) {
        return stats.popularBibs(days, limit);
    }

    @GetMapping("/bibs/{bibId}")
    @Operation(summary = "按书目：副本状态分布、活跃预约数、在借数")
    public BibStats bib(@PathVariable Long bibId) {
        return stats.bibStats(bibId);
    }

    @GetMapping("/readers")
    @Operation(summary = "按读者：排队/在借/逾期/争议/未缴罚款；可按借书证过滤")
    public List<ReaderStatsRow> readers(@RequestParam(required = false) String cardNo) {
        return stats.readerOverview(cardNo);
    }

    @GetMapping("/batches")
    @Operation(summary = "按物流批次：包裹数/已到馆/丢包/在途/平均时效")
    public List<BatchStatsRow> batches() {
        return stats.batchStats();
    }
}
