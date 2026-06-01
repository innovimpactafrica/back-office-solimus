package com.example.solimus.repositories;

import com.example.solimus.entities.MeetingAgendaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingAgendaItemRepository extends JpaRepository<MeetingAgendaItem, Long> {

    List<MeetingAgendaItem> findByMeetingIdOrderByOrderIndexAsc(Long meetingId);
}
