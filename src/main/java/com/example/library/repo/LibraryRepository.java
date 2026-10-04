package com.example.library.repo;

import com.example.library.domain.Library;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LibraryRepository extends JpaRepository<Library, Long> {
    Optional<Library> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Library l where l.id = :id")
    Optional<Library> lockById(Long id);
}
