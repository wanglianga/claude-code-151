package com.citylibrary.pll.web;

import com.citylibrary.pll.domain.CopyCondition;
import com.citylibrary.pll.dto.Requests;
import com.citylibrary.pll.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "还书核验", description = "工作人员扫条码核验馆藏状态、污损、缺页、附件、逾期")
@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @Operation(summary = "归还核验并入库",
            description = "condition 取值 GOOD/WORN/DAMAGED/SEVERE；missingPages/attachmentsMissing 触发严重污损赔偿")
    @PostMapping
    public ReturnService.ReturnResult returnBook(
            @Valid @RequestBody Requests.ReturnRequest req,
            @RequestHeader(value = "X-Operator", defaultValue = "STAFF") String operator) {
        CopyCondition condition;
        try {
            condition = CopyCondition.valueOf(
                    (req.condition() == null || req.condition().isBlank())
                            ? "GOOD" : req.condition().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("condition 必须为 GOOD/WORN/DAMAGED/SEVERE");
        }
        return returnService.returnBook(req.barcode(), req.returnLibrary(), condition,
                req.missingPages(), req.attachmentsMissing(), req.damageNote(), operator);
    }
}
