package com.pgds.repo;

import com.pgds.domain.Complaint;
import com.pgds.domain.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Page<Complaint> findByStatus(ComplaintStatus status, Pageable pageable);
    Page<Complaint> findBySubmittedByUsername(String username, Pageable pageable);
    long countByStatus(ComplaintStatus status);

    @Query("select new com.pgds.repo.LabelCount(c.category, count(c)) from Complaint c group by c.category")
    List<LabelCount> countByCategory();
}
