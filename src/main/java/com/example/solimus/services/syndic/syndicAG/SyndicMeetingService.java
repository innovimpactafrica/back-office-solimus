package com.example.solimus.services.syndic.syndicAG;

import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.syndic.meeting.CreateMeetingDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface SyndicMeetingService {

    MeetingDetailDTO createMeeting(CreateMeetingDTO dto, List<MultipartFile> documents);

}
