package com.example.library.web;

import com.example.library.service.CatalogService;
import com.example.library.web.dto.CatalogView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@Tag(name = "1. 书目检索", description = "本馆库存 / 跨馆可调拨库存 / 预约队列 / 预计到馆 / 电子资源")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/search")
    @Operation(summary = "按书名/作者/ISBN 检索；pickupLibraryCode 传取书分馆以计算可调拨库存与 ETA")
    public List<CatalogView> search(@RequestParam String keyword,
                                    @RequestParam(required = false) String pickupLibraryCode) {
        return catalog.search(keyword, pickupLibraryCode);
    }

    @GetMapping("/bibs/{bibId}")
    @Operation(summary = "查看单部书目的完整库存视图")
    public CatalogView view(@PathVariable Long bibId,
                            @RequestParam(required = false) String pickupLibraryCode) {
        return catalog.view(bibId, pickupLibraryCode);
    }
}
