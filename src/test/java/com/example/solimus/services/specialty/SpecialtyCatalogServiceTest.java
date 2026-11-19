package com.example.solimus.services.specialty;

import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.entities.Specialty;
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.services.minio.MinioService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SpecialtyCatalogServiceTest {

    private final SpecialtyRepository repository = mock(SpecialtyRepository.class);
    private final MinioService minio = mock(MinioService.class);
    private final SpecialtyCatalogService service = new SpecialtyCatalogService(repository, minio);

    @Test
    void listsAllSortedOrFiltersByName() {
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(specialty(1L, "Menuiserie", "plumbing")));
        when(repository.findByNameContainingIgnoreCaseOrderByNameAsc("plomb")).thenReturn(List.of(specialty(2L, "Plomberie", null)));

        assertThat(service.listAll(null)).extracting(SpecialtyDTO::getName).containsExactly("Menuiserie");
        assertThat(service.listAll("  ")).extracting(SpecialtyDTO::getName).containsExactly("Menuiserie");
        assertThat(service.listAll(" plomb ")).extracting(SpecialtyDTO::getName).containsExactly("Plomberie");
    }

    @Test
    void uploadedIconGetsAPublicUrlLocalIconNameDoesNot() {
        when(minio.getFileUrl("abc.png")).thenReturn("https://minio/bucket/abc.png");

        assertThat(service.toDto(specialty(1L, "Plomberie", "abc.png")).getIconUrl()).isEqualTo("https://minio/bucket/abc.png");
        assertThat(service.toDto(specialty(2L, "Électricité", "electrical")).getIconUrl()).isNull();
        assertThat(service.toDto(specialty(3L, "Peinture", null)).getIconUrl()).isNull();
    }

    private static Specialty specialty(Long id, String name, String icon) {
        Specialty s = new Specialty();
        s.setId(id);
        s.setName(name);
        s.setIcon(icon);
        return s;
    }
}
