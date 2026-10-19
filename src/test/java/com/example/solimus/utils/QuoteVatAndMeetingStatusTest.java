package com.example.solimus.utils;

import com.example.solimus.dtos.provider.request.CreateQuoteDTO;
import com.example.solimus.entities.Meeting;
import com.example.solimus.entities.Quote;
import com.example.solimus.entities.QuoteItem;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.QuoteItemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteVatAndMeetingStatusTest {

    private static QuoteItem item(QuoteItemType type, int qty, String unitPrice) {
        QuoteItem i = new QuoteItem();
        i.setType(type);
        i.setQuantity(qty);
        i.setUnitPrice(new BigDecimal(unitPrice));
        return i;
    }

    private static Quote quote(BigDecimal taxRate) {
        Quote q = new Quote();
        q.setItems(new ArrayList<>(List.of(
                item(QuoteItemType.LABOR, 1, "10000"),
                item(QuoteItemType.MATERIAL, 3, "3333.33"))));
        q.setTaxRate(taxRate);
        q.calculateTotals();
        return q;
    }

    @Test
    void vatIsAppliedServerSide_at18Percent() {
        Quote q = quote(VatUtil.VAT_RATE);
        // HT = 10000 + 9999.99 = 19999.99 ; TVA = 3599.9982 → 3600.00 ; TTC = 23599.99
        assertThat(q.getHtAmount()).isEqualByComparingTo("19999.99");
        assertThat(q.getTvaAmount()).isEqualByComparingTo("3600.00");
        assertThat(q.getTotalAmount()).isEqualByComparingTo("23599.99");
    }

    @Test
    void legacyQuoteWithoutRate_keepsOriginalTotal_andReadsNullSafe() {
        Quote q = quote(null);
        assertThat(q.getTvaAmount()).isEqualByComparingTo("0");
        assertThat(q.getTotalAmount()).isEqualByComparingTo("19999.99");

        Quote raw = new Quote(); // colonnes ht/tva/tax_rate nulles (avant correction)
        raw.setLaborTotalAmount(new BigDecimal("100"));
        raw.setMaterialTotalAmount(new BigDecimal("50"));
        raw.setTotalAmount(new BigDecimal("150"));
        assertThat(VatUtil.htOf(raw)).isEqualByComparingTo("150");
        assertThat(VatUtil.tvaOf(raw)).isEqualByComparingTo("0");
        assertThat(VatUtil.taxRateOf(raw)).isEqualByComparingTo("0");
    }

    @Test
    void createQuoteDto_acceptsIsDraft_andIgnoresTaxRate() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CreateQuoteDTO draft = mapper.readValue(
                "{\"interventionRequestId\":1,\"estimatedDelayId\":2,\"isDraft\":true,\"taxRate\":5,"
                        + "\"items\":[{\"description\":\"x\",\"quantity\":1,\"unitPrice\":100,\"type\":\"LABOR\"}]}",
                CreateQuoteDTO.class);
        assertThat(draft.isDraft()).isTrue();

        CreateQuoteDTO legacy = mapper.readValue(
                "{\"interventionRequestId\":1,\"estimatedDelayId\":2,\"draft\":true}", CreateQuoteDTO.class);
        assertThat(legacy.isDraft()).isTrue();
    }

    @Test
    void meetingStatus_isDerivedFromDate() {
        Meeting m = new Meeting();
        m.setStatus(MeetingStatus.UPCOMING);
        m.setMeetingDate(LocalDate.of(2026, 6, 15));
        m.setStartTime(LocalTime.of(10, 0));
        m.setEndTime(LocalTime.of(12, 0));

        assertThat(MeetingStatusUtil.effectiveStatus(m, LocalDateTime.of(2026, 6, 15, 9, 0))).isEqualTo(MeetingStatus.UPCOMING);
        assertThat(MeetingStatusUtil.effectiveStatus(m, LocalDateTime.of(2026, 6, 15, 11, 0))).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(MeetingStatusUtil.effectiveStatus(m, LocalDateTime.of(2026, 6, 16, 9, 0))).isEqualTo(MeetingStatus.COMPLETED);

        m.setStatus(MeetingStatus.CANCELLED);
        assertThat(MeetingStatusUtil.effectiveStatus(m, LocalDateTime.of(2026, 6, 16, 9, 0))).isEqualTo(MeetingStatus.CANCELLED);
    }
}
