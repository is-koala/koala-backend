package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentDao extends JpaRepository<Department, UUID> {

    List<Department> findAllByFacultyIdOrderByNameAsc(UUID facultyId);
}
