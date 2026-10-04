package com.example.library.web;

import com.example.library.domain.Policy;
import com.example.library.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/policies")
@Tag(name = "9. 规则参数（管理端）", description = "借阅/预约/罚款/调拨保护比例等可调规则及说明")
public class PolicyController {

    private final PolicyService policies;

    public PolicyController(PolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    @Operation(summary = "全部规则参数（代码内 key + 当前值）")
    public Map<String, Double> all() {
        return policies.all();
    }

    @PutMapping
    @Operation(summary = "（馆员）调整规则参数值，例如把 transfer.protectionRatio 提高以加强远端分馆保护")
    public Map<String, Object> update(@Valid @RequestBody com.example.library.web.dto.PolicyUpdateRequest req) {
        Policy p = policies.update(req.key(), req.value());
        return Map.of("key", p.getKey(), "value", p.getNumericValue(),
                "description", p.getDescription() == null ? "" : p.getDescription());
    }
}
