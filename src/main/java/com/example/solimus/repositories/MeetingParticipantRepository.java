package com.example.solimus.repositories;

import com.example.solimus.entities.MeetingParticipant;
import com.example.solimus.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findByMeetingId(Long meetingId);

    List<MeetingParticipant> findByUserId(Long userId);

    List<MeetingParticipant> findByUser(User user);
}
