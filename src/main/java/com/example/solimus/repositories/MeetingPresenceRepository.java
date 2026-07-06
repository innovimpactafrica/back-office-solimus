package com.example.solimus.repositories;

import com.example.solimus.entities.MeetingPresence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingPresenceRepository extends JpaRepository<MeetingPresence, Long> {

    List<MeetingPresence> findByMeetingParticipantMeetingId(Long meetingId);
}
