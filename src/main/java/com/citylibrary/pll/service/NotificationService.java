package com.citylibrary.pll.service;

import com.citylibrary.pll.domain.Notice;
import com.citylibrary.pll.domain.NoticeType;
import com.citylibrary.pll.domain.Loan;
import com.citylibrary.pll.domain.Patron;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.NoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 读者通知：本实现落库为站内信（IN_APP），到馆、超时释放、取消、
 * 逾期、异常处理结论都会通知。真实环境可在此扩展短信/邮件通道。
 */
@Service
public class NotificationService {

    private final NoticeRepository noticeRepository;

    public NotificationService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    @Transactional
    public void notify(Patron patron, NoticeType type, String title, String content) {
        notify(patron, null, type, title, content);
    }

    @Transactional
    public void notify(Patron patron, Loan loan, NoticeType type, String title, String content) {
        Notice n = new Notice();
        n.setPatron(patron);
        n.setLoan(loan);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        noticeRepository.save(n);
    }

    @Transactional(readOnly = true)
    public boolean existsByTypeAndLoan(NoticeType type, Long loanId) {
        return noticeRepository.existsByTypeAndLoanId(type, loanId);
    }
}
