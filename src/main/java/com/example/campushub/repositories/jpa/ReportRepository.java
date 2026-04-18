package com.example.campushub.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.jpa.Report;

@Repository
public interface ReportRepository extends JpaRepository<Report, String>{
    
}
