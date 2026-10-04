package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.Library;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LibraryRepository extends JpaRepository<Library, Long> {
    Optional<Library> findByCode(String code);
    List<Library> findByTempClosedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Library l where l.id = :id")
    Optional<Library> findLockedById(Long id);
}
