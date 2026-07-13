package com.example.solimus.services.owner.meeting;

import com.example.solimus.dtos.owner.meeting.OwnerMeetingSearchFilterDTO;
import com.example.solimus.dtos.owner.meeting.OwnerMeetingsTabResponseDTO;

public interface OwnerMeetingService {

    OwnerMeetingsTabResponseDTO getMeetingsTab(Long userId, Long residenceId, OwnerMeetingSearchFilterDTO filter);
}
