package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Report;
import com.exskylab.koala.entities.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportDao extends JpaRepository<Report, UUID> {

    Page<Report> findAllByStatus(ReportStatus status, Pageable pageable);

    Page<Report> findAllByReporterId(UUID reporterId, Pageable pageable);
}
