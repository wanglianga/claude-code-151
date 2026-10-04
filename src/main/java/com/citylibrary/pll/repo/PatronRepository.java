package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.Patron;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatronRepository extends JpaRepository<Patron, Long> {
    Optional<Patron> findByCardNo(String cardNo);
}
