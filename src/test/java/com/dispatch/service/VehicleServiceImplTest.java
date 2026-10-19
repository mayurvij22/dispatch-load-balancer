package com.dispatch.service;

import com.dispatch.dto.VehicleRequest;
import com.dispatch.entity.Vehicle;
import com.dispatch.exception.DuplicateIdException;
import com.dispatch.mapper.VehicleMapper;
import com.dispatch.repository.VehicleRepository;
import com.dispatch.service.impl.VehicleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.dispatch.TestFixtures.vehicleRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Captor
    private ArgumentCaptor<List<Vehicle>> savedCaptor;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleServiceImpl(vehicleRepository, new VehicleMapper());
    }

    @Test
    @DisplayName("new vehicles are mapped from the request and saved together")
    void insertsNewVehicles() {
        when(vehicleRepository.findAllById(anyList())).thenReturn(List.of());

        int saved = vehicleService.upsertVehicles(List.of(vehicleRequest("VEH001", 100), vehicleRequest("VEH002", 80)));

        assertThat(saved).isEqualTo(2);
        verify(vehicleRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).satisfiesExactly(
                v -> {
                    assertThat(v.getVehicleId()).isEqualTo("VEH001");
                    assertThat(v.getCapacity()).isEqualTo(100);
                    assertThat(v.getCurrentAddress()).isEqualTo("Karol Bagh, Delhi, India");
                    assertThat(v.isNew()).isTrue();
                },
                v -> assertThat(v.getVehicleId()).isEqualTo("VEH002"));
    }

    @Test
    @DisplayName("re-posting a vehicleId updates the existing vehicle in place")
    void updatesExistingVehicle() {
        Vehicle existing = new Vehicle("VEH001", 50, 0, 0, "Old depot");
        when(vehicleRepository.findAllById(anyList())).thenReturn(List.of(existing));

        vehicleService.upsertVehicles(List.of(new VehicleRequest("VEH001", 150.0, 12.97, 77.64, "Indiranagar")));

        verify(vehicleRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).singleElement().isSameAs(existing);
        assertThat(existing.getCapacity()).isEqualTo(150);
        assertThat(existing.getCurrentLatitude()).isEqualTo(12.97);
        assertThat(existing.getCurrentLongitude()).isEqualTo(77.64);
        assertThat(existing.getCurrentAddress()).isEqualTo("Indiranagar");
    }

    @Test
    @DisplayName("duplicate vehicleIds in one request are rejected")
    void rejectsDuplicateIds() {
        List<VehicleRequest> request = List.of(vehicleRequest("VEH001", 100), vehicleRequest("VEH001", 90));

        assertThatThrownBy(() -> vehicleService.upsertVehicles(request))
                .isInstanceOfSatisfying(DuplicateIdException.class, ex -> {
                    assertThat(ex.getField()).isEqualTo("vehicleId");
                    assertThat(ex.getDuplicateIds()).containsExactly("VEH001");
                    assertThat(ex.getMessage()).contains("VEH001");
                });
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    @DisplayName("deleteAllVehicles issues a single bulk delete")
    void deleteAllUsesBatchDelete() {
        vehicleService.deleteAllVehicles();

        verify(vehicleRepository).deleteAllInBatch();
    }
}
