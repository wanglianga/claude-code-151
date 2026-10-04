package com.citylibrary.pll.web;

import com.citylibrary.pll.dto.CatalogView;
import com.citylibrary.pll.service.CatalogService;
import com.citylibrary.pll.service.RuleBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "目录与规则", description = "图书搜索（本馆/跨馆库存、排队、预计到馆、电子资源）与规则手册")
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;
    private final RuleBookService ruleBookService;

    public CatalogController(CatalogService catalogService, RuleBookService ruleBookService) {
        this.catalogService = catalogService;
        this.ruleBookService = ruleBookService;
    }

    @Operation(summary = "搜索图书", description = "按书名/作者/ISBN 搜索，pickup 为读者取书馆代码")
    @GetMapping("/catalog/search")
    public List<CatalogView> search(@RequestParam String q,
                                    @RequestParam(defaultValue = "CENTRAL") String pickup) {
        return catalogService.search(q, pickup);
    }

    @Operation(summary = "书目详情（库存聚合视图）")
    @GetMapping("/catalog/{bookId}")
    public CatalogView view(@PathVariable Long bookId,
                            @RequestParam(defaultValue = "CENTRAL") String pickup) {
        return catalogService.view(bookId, pickup);
    }

    @Operation(summary = "规则手册", description = "所有预约/调拨/借阅/罚款规则编号与原文")
    @GetMapping("/rules")
    public Map<String, String> rules() {
        return ruleBookService.allRules();
    }
}
