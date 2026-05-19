package com.example.solimus.repositories;

import com.example.solimus.entities.InterventionComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterventionCommentRepository extends JpaRepository<InterventionComment, Long> {
}
