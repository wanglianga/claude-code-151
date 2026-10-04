package com.example.library.web;

import com.example.library.service.LibraryAdminService;
import com.example.library.web.dto.ClosureRequest;
import com.example.library.web.dto.LibraryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/libraries")
@Tag(name = "8. 分馆与临时闭馆", description = "闭馆期间取书时限/应还日期自动顺延")
public class LibraryController {

    private final LibraryAdminService admin;

    public LibraryController(LibraryAdminService admin) {
        this.admin = admin;
    }

    @GetMapping
    @Operation(summary = "全部分馆及临时闭馆状态")
    public List<LibraryView> list() {
        return admin.views();
    }

    @PostMapping("/{code}/closure")
    @Operation(summary = "（馆员）设置/解除临时闭馆；闭馆自动顺延在该馆待取预约与临期借阅")
    public LibraryView closure(@PathVariable String code, @Valid @RequestBody ClosureRequest req) {
        if (!req.closed()) {
            return admin.reopenView(code);
        }
        return admin.setClosureView(code, true, req.reason());
    }

    @PostMapping("/{code}/reopen")
    @Operation(summary = "（馆员）恢复开馆")
    public LibraryView reopen(@PathVariable String code) {
        return admin.reopenView(code);
    }
}
