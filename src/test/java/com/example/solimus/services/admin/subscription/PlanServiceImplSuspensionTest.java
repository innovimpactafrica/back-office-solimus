package com.example.solimus.services.admin.subscription;

import com.example.solimus.controllers.AdminSubscriptionController;
import com.example.solimus.dtos.admin.subscription.SuspendSubscriberDTO;
import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.GlobalExceptionHandler;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.auth.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Tests unitaires (sans base) de la suspension d'un abonné — ADM-PRE-03 / ADM-SYN-08
@ExtendWith(MockitoExtension.class)
class PlanServiceImplSuspensionTest {

    @Mock private SyndicSubscriptionRepository syndicSubscriptionRepository;
    @Mock private ProviderSubscriptionRepository providerSubscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    @InjectMocks private PlanServiceImpl planService;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setFirstName("Test");
        u.setEmail("user" + id + "@example.test");
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private static SuspendSubscriberDTO dto(boolean notify) {
        return SuspendSubscriberDTO.builder().reason("Impayé").notifyClient(notify).build();
    }

    @Test
    void suspendSyndic_disablesAccountAndSubscription_keepsPaymentHistory() {
        User syndic = user(1L);
        SyndicSubscription sub = new SyndicSubscription();
        sub.setId(10L);
        sub.setSyndic(syndic);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPaymentStatus(PaymentStatus.COMPLETED);
        when(syndicSubscriptionRepository.findById(10L)).thenReturn(Optional.of(sub));

        planService.suspendSubscriber(10L, SubscriberType.SYNDIC, dto(true));

        assertThat(syndic.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(syndic.getSuspensionReason()).isEqualTo("Impayé");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.DESACTIVATED);
        assertThat(sub.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(syndicSubscriptionRepository).save(sub);
        verify(emailService).sendEmail(any(), anyString(), anyString());
        verifyNoInteractions(providerSubscriptionRepository);
    }

    @Test
    void suspendProvider_disablesAccountAndProviderSubscription() {
        User provider = user(2L);
        ProviderSubscription sub = new ProviderSubscription();
        sub.setId(20L);
        sub.setProvider(provider);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPaymentStatus(PaymentStatus.COMPLETED);
        when(providerSubscriptionRepository.findById(20L)).thenReturn(Optional.of(sub));

        planService.suspendSubscriber(20L, SubscriberType.PRESTATAIRE, dto(false));

        assertThat(provider.getStatus()).isEqualTo(UserStatus.DISABLED);
        // Valeur écrite dans subscriptions.status : doit exister dans la colonne (cf. script SQL)
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.DESACTIVATED);
        assertThat(sub.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(providerSubscriptionRepository).save(sub);
        verify(emailService, never()).sendEmail(any(), anyString(), anyString());
        verifyNoInteractions(syndicSubscriptionRepository);
    }

    @Test
    void suspendProvider_emailFailure_doesNotFailSuspension() {
        User provider = user(3L);
        ProviderSubscription sub = new ProviderSubscription();
        sub.setProvider(provider);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        when(providerSubscriptionRepository.findById(30L)).thenReturn(Optional.of(sub));
        doThrow(new RuntimeException("SMTP down")).when(emailService).sendEmail(any(), anyString(), anyString());

        assertThatCode(() -> planService.suspendSubscriber(30L, SubscriberType.PRESTATAIRE, dto(true)))
                .doesNotThrowAnyException();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.DESACTIVATED);
    }

    @Test
    void suspend_unknownSubscription_throws404_andTouchesNothing() {
        when(providerSubscriptionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.suspendSubscriber(99L, SubscriberType.PRESTATAIRE, dto(true)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(userRepository, emailService);
    }

    @Test
    void suspendProvider_persistenceFailure_propagates_soTransactionRollsBack() throws Exception {
        User provider = user(4L);
        ProviderSubscription sub = new ProviderSubscription();
        sub.setProvider(provider);
        when(providerSubscriptionRepository.findById(40L)).thenReturn(Optional.of(sub));
        when(providerSubscriptionRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("Data truncated for column 'status'"));

        // L'erreur n'est pas avalée : @Transactional annule aussi la désactivation du compte
        assertThatThrownBy(() -> planService.suspendSubscriber(40L, SubscriberType.PRESTATAIRE, dto(true)))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(emailService, never()).sendEmail(any(), anyString(), anyString());
        assertThat(PlanServiceImpl.class
                .getMethod("suspendSubscriber", Long.class, SubscriberType.class, SuspendSubscriberDTO.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void suspend_invalidSubscriberType_returns400() throws Exception {
        PlanService service = mock(PlanService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminSubscriptionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/admin/subscriptions/subscribers/1/suspend")
                        .param("subscriberType", "INCONNU")
                        .contentType("application/json")
                        .content("{\"reason\":\"x\",\"notifyClient\":false}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/subscriptions/subscribers/1/suspend")
                        .contentType("application/json")
                        .content("{\"reason\":\"x\",\"notifyClient\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
