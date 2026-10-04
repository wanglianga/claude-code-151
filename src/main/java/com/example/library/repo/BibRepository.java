package com.example.library.repo;

import com.example.library.domain.Bib;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BibRepository extends JpaRepository<Bib, Long> {
    Optional<Bib> findByIsbn(String isbn);

    @Query("""
            select b from Bib b
            where lower(b.title) like lower(concat('%', :kw, '%'))
               or lower(b.author) like lower(concat('%', :kw, '%'))
               or lower(b.isbn) = lower(:kw)
            order by b.title
            """)
    List<Bib> search(String kw);
}
