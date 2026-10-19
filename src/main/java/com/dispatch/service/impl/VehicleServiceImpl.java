package com.dispatch.service.impl;

import com.dispatch.dto.VehicleRequest;
import com.dispatch.entity.Vehicle;
import com.dispatch.mapper.VehicleMapper;
import com.dispatch.repository.VehicleRepository;
import com.dispatch.service.VehicleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VehicleServiceImpl implements VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;

    public VehicleServiceImpl(VehicleRepository vehicleRepository, VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    @Transactional
    public int upsertVehicles(List<VehicleRequest> vehicles) {
        List<Vehicle> entities = UpsertSupport.prepareUpsert(vehicles, VehicleRequest::vehicleId, "vehicleId",
                vehicleRepository, Vehicle::getVehicleId, vehicleMapper::toEntity, vehicleMapper::updateEntity);
        vehicleRepository.saveAll(entities);
        log.info("Upserted {} vehicle(s)", entities.size());
        return entities.size();
    }

    @Override
    @Transactional
    public void deleteAllVehicles() {
        vehicleRepository.deleteAllInBatch();
        log.info("Deleted all vehicles");
    }
}
