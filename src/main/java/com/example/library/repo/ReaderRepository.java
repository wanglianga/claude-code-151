package com.example.library.repo;

import com.example.library.domain.Reader;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReaderRepository extends JpaRepository<Reader, Long> {
    Optional<Reader> findByCardNo(String cardNo);
}
