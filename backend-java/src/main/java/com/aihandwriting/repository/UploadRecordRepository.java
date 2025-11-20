package com.aihandwriting.repository;

import com.aihandwriting.model.UploadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadRecordRepository extends JpaRepository<UploadRecord, Long> {

}
