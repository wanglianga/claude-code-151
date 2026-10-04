package com.citylibrary.pll.web;

import com.citylibrary.pll.domain.Notice;
import com.citylibrary.pll.domain.Patron;
import com.citylibrary.pll.dto.Views;
import com.citylibrary.pll.exception.NotFoundException;
import com.citylibrary.pll.repo.NoticeRepository;
import com.citylibrary.pll.repo.PatronRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "读者通知", description = "到馆取书、超时释放、闭馆、罚款等站内通知")
@RestController
@RequestMapping("/api/patrons/{cardNo}/notices")
public class NoticeController {

    private final NoticeRepository noticeRepository;
    private final PatronRepository patronRepository;

    public NoticeController(NoticeRepository noticeRepository, PatronRepository patronRepository) {
        this.noticeRepository = noticeRepository;
        this.patronRepository = patronRepository;
    }

    @Operation(summary = "读者全部通知")
    @GetMapping
    public List<Views.NoticeView> list(@PathVariable String cardNo) {
        Patron p = patronRepository.findByCardNo(cardNo)
                .orElseThrow(() -> new NotFoundException("读者证不存在: " + cardNo));
        return noticeRepository.findByPatronIdOrderByCreatedAtDesc(p.getId())
                .stream().map(Views::of).toList();
    }

    @Operation(summary = "标记通知已读")
    @PostMapping("/{id}/read")
    public void read(@PathVariable String cardNo, @PathVariable Long id) {
        Notice n = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("通知不存在: " + id));
        if (!n.getPatron().getCardNo().equals(cardNo)) {
            throw new IllegalArgumentException("通知不属于该读者");
        }
        n.setReadFlag(true);
        noticeRepository.save(n);
    }
}
