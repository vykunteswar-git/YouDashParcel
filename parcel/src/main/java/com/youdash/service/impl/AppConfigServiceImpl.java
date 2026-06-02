package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppConfigDTO;
import com.youdash.dto.CheckoutPaymentOptionsDTO;
import com.youdash.dto.OutstationLegRateTierDTO;
import com.youdash.dto.WeightCostSlabDTO;
import com.youdash.entity.WeightCostSlabEntity;
import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.OutstationLegRateTierEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.model.OutstationLegType;
import com.youdash.model.PaymentType;
import com.youdash.repository.AppConfigRepository;
import com.youdash.repository.OutstationLegRateTierRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.repository.WeightCostSlabRepository;
import com.youdash.service.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AppConfigServiceImpl implements AppConfigService {

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired
    private OutstationLegRateTierRepository legRateTierRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private WeightCostSlabRepository weightCostSlabRepository;

    @Override
    public ApiResponse<AppConfigDTO> getConfig() {
        ApiResponse<AppConfigDTO> response = new ApiResponse<>();
        try {
            AppConfigEntity e = appConfigRepository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("Config not found"));
            response.setData(toDto(e));
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public ApiResponse<AppConfigDTO> updateConfig(AppConfigDTO dto) {
        ApiResponse<AppConfigDTO> response = new ApiResponse<>();
        try {
            AppConfigEntity e = appConfigRepository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("Config not found"));
            if (dto.getGstPercent() != null) {
                e.setGstPercent(dto.getGstPercent());
            }
            if (dto.getIncityPlatformFee() != null) {
                e.setIncityPlatformFee(dto.getIncityPlatformFee());
            }
            if (dto.getOutstationPlatformFee() != null) {
                e.setOutstationPlatformFee(dto.getOutstationPlatformFee());
            }
            applyLegacyPlatformFeeInput(e, dto);
            if (dto.getPickupRatePerKm() != null) {
                e.setPickupRatePerKm(dto.getPickupRatePerKm());
            }
            if (dto.getDropRatePerKm() != null) {
                e.setDropRatePerKm(dto.getDropRatePerKm());
            }
            if (dto.getPerKgRate() != null) {
                e.setPerKgRate(dto.getPerKgRate());
            }
            if (dto.getDefaultRouteRatePerKm() != null) {
                e.setDefaultRouteRatePerKm(dto.getDefaultRouteRatePerKm());
            }
            if (dto.getCodEnabled() != null) {
                e.setCodEnabled(dto.getCodEnabled());
            }
            if (dto.getOnlineEnabled() != null) {
                e.setOnlineEnabled(dto.getOnlineEnabled());
            }
            if (dto.getDefaultPaymentType() != null) {
                e.setDefaultPaymentType(dto.getDefaultPaymentType());
            }
            validatePaymentToggleCombination(e);
            AppConfigEntity saved = appConfigRepository.save(e);

            if (dto.getPickupLegTiers() != null) {
                replaceLegTiers(OutstationLegType.PICKUP, dto.getPickupLegTiers());
            }
            if (dto.getDropLegTiers() != null) {
                replaceLegTiers(OutstationLegType.DROP, dto.getDropLegTiers());
            }
            if (dto.getWeightCostSlabs() != null) {
                replaceWeightCostSlabs(dto.getWeightCostSlabs());
            }

            response.setData(toDto(saved));
            response.setMessage("Config updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<CheckoutPaymentOptionsDTO> getCheckoutPaymentOptions() {
        ApiResponse<CheckoutPaymentOptionsDTO> response = new ApiResponse<>();
        try {
            AppConfigEntity e = appConfigRepository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("Config not found"));
            List<PaymentType> available = resolveAvailablePaymentTypes(e.getCodEnabled(), e.getOnlineEnabled());
            PaymentType defaultType = e.getDefaultPaymentType();
            if (defaultType == null || !available.contains(defaultType)) {
                defaultType = available.isEmpty() ? null : available.get(0);
            }
            CheckoutPaymentOptionsDTO data = CheckoutPaymentOptionsDTO.builder()
                    .codEnabled(Boolean.TRUE.equals(e.getCodEnabled()))
                    .onlineEnabled(Boolean.TRUE.equals(e.getOnlineEnabled()))
                    .defaultPaymentType(defaultType)
                    .availablePaymentTypes(available)
                    .build();
            response.setData(data);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private void replaceLegTiers(OutstationLegType legType, List<OutstationLegRateTierDTO> tiers) {
        validateTierList(legType, tiers);
        legRateTierRepository.deleteByLegType(legType);
        int order = 0;
        for (OutstationLegRateTierDTO t : tiers) {
            OutstationLegRateTierEntity row = new OutstationLegRateTierEntity();
            row.setLegType(legType);
            row.setMinWeightKg(t.getMinWeightKg());
            row.setMaxWeightKg(t.getMaxWeightKg());
            row.setVehicleId(t.getVehicleId());
            row.setBaseFare(t.getBaseFare());
            row.setMinimumKm(t.getMinimumKm());
            row.setRatePerKm(t.getRatePerKm());
            row.setSortOrder(t.getSortOrder() != null ? t.getSortOrder() : order);
            row.setIsActive(t.getIsActive() == null || Boolean.TRUE.equals(t.getIsActive()));
            legRateTierRepository.save(row);
            order++;
        }
    }

    private static void validateTierList(OutstationLegType legType, List<OutstationLegRateTierDTO> tiers) {
        if (tiers == null) {
            return;
        }
        List<OutstationLegRateTierDTO> active = tiers.stream()
                .filter(t -> t.getIsActive() == null || Boolean.TRUE.equals(t.getIsActive()))
                .sorted(Comparator.comparing(t -> nz(t.getMinWeightKg())))
                .toList();
        for (OutstationLegRateTierDTO t : active) {
            double min = nz(t.getMinWeightKg());
            double max = nz(t.getMaxWeightKg());
            if (min < 0 || max <= min) {
                throw new RuntimeException(legType + " tier: max weight must be greater than min weight");
            }
            if (t.getVehicleId() == null) {
                throw new RuntimeException(legType + " tier: vehicle must be selected");
            }
            if (nz(t.getBaseFare()) < 0) {
                throw new RuntimeException(legType + " tier: base fare cannot be negative");
            }
            if (nz(t.getMinimumKm()) < 0) {
                throw new RuntimeException(legType + " tier: minimum km cannot be negative");
            }
            if (nz(t.getRatePerKm()) < 0) {
                throw new RuntimeException(legType + " tier: rate per km cannot be negative");
            }
        }
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                if (rangesOverlap(active.get(i), active.get(j))) {
                    throw new RuntimeException(legType + " tiers must not overlap (kg ranges)");
                }
            }
        }
    }

    private static boolean rangesOverlap(OutstationLegRateTierDTO a, OutstationLegRateTierDTO b) {
        double aMin = nz(a.getMinWeightKg());
        double aMax = nz(a.getMaxWeightKg());
        double bMin = nz(b.getMinWeightKg());
        double bMax = nz(b.getMaxWeightKg());
        return aMin < bMax && bMin < aMax;
    }

    private AppConfigDTO toDto(AppConfigEntity e) {
        AppConfigDTO d = new AppConfigDTO();
        d.setId(e.getId());
        d.setGstPercent(e.getGstPercent());
        Double legacy = e.getPlatformFee();
        d.setIncityPlatformFee(
                e.getIncityPlatformFee() != null ? e.getIncityPlatformFee() : legacy);
        d.setOutstationPlatformFee(
                e.getOutstationPlatformFee() != null ? e.getOutstationPlatformFee() : legacy);
        setDeprecatedLegacyPlatformFee(d, legacy);
        d.setPickupRatePerKm(e.getPickupRatePerKm());
        d.setDropRatePerKm(e.getDropRatePerKm());
        d.setPerKgRate(e.getPerKgRate());
        d.setDefaultRouteRatePerKm(e.getDefaultRouteRatePerKm());
        d.setCodEnabled(e.getCodEnabled());
        d.setOnlineEnabled(e.getOnlineEnabled());
        d.setDefaultPaymentType(e.getDefaultPaymentType());
        d.setPickupLegTiers(tiersToDto(OutstationLegType.PICKUP));
        d.setDropLegTiers(tiersToDto(OutstationLegType.DROP));
        d.setWeightCostSlabs(weightSlabsToDto());
        return d;
    }

    private List<OutstationLegRateTierDTO> tiersToDto(OutstationLegType legType) {
        return legRateTierRepository.findByLegTypeOrderBySortOrderAscMinWeightKgAsc(legType).stream()
                .map(this::tierEntityToDto)
                .toList();
    }

    private OutstationLegRateTierDTO tierEntityToDto(OutstationLegRateTierEntity row) {
        OutstationLegRateTierDTO d = new OutstationLegRateTierDTO();
        d.setId(row.getId());
        d.setLegType(row.getLegType());
        d.setMinWeightKg(row.getMinWeightKg());
        d.setMaxWeightKg(row.getMaxWeightKg());
        d.setVehicleId(row.getVehicleId());
        d.setBaseFare(row.getBaseFare());
        d.setMinimumKm(row.getMinimumKm());
        d.setRatePerKm(row.getRatePerKm());
        d.setSortOrder(row.getSortOrder());
        d.setIsActive(row.getIsActive());
        if (row.getVehicleId() != null) {
            vehicleRepository.findById(row.getVehicleId())
                    .map(VehicleEntity::getName)
                    .ifPresent(d::setVehicleName);
        }
        return d;
    }

    private void replaceWeightCostSlabs(List<WeightCostSlabDTO> slabs) {
        validateWeightSlabList(slabs);
        weightCostSlabRepository.deleteAll();
        int order = 0;
        for (WeightCostSlabDTO s : slabs) {
            WeightCostSlabEntity row = new WeightCostSlabEntity();
            row.setMinWeightKg(s.getMinWeightKg());
            row.setMaxWeightKg(s.getMaxWeightKg());
            row.setFlatCost(s.getFlatCost());
            row.setSortOrder(s.getSortOrder() != null ? s.getSortOrder() : order);
            row.setIsActive(s.getIsActive() == null || Boolean.TRUE.equals(s.getIsActive()));
            weightCostSlabRepository.save(row);
            order++;
        }
    }

    private List<WeightCostSlabDTO> weightSlabsToDto() {
        return weightCostSlabRepository.findAllByOrderBySortOrderAscMinWeightKgAsc().stream()
                .map(row -> {
                    WeightCostSlabDTO d = new WeightCostSlabDTO();
                    d.setId(row.getId());
                    d.setMinWeightKg(row.getMinWeightKg());
                    d.setMaxWeightKg(row.getMaxWeightKg());
                    d.setFlatCost(row.getFlatCost());
                    d.setSortOrder(row.getSortOrder());
                    d.setIsActive(row.getIsActive());
                    return d;
                })
                .toList();
    }

    private static void validateWeightSlabList(List<WeightCostSlabDTO> slabs) {
        if (slabs == null) return;
        List<WeightCostSlabDTO> active = slabs.stream()
                .filter(s -> s.getIsActive() == null || Boolean.TRUE.equals(s.getIsActive()))
                .sorted(Comparator.comparing(s -> nz(s.getMinWeightKg())))
                .toList();
        for (WeightCostSlabDTO s : active) {
            double min = nz(s.getMinWeightKg());
            double max = nz(s.getMaxWeightKg());
            if (min < 0 || max <= min) {
                throw new RuntimeException("Weight slab: max weight must be greater than min weight");
            }
            if (nz(s.getFlatCost()) < 0) {
                throw new RuntimeException("Weight slab: flat cost cannot be negative");
            }
        }
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                double aMin = nz(active.get(i).getMinWeightKg()), aMax = nz(active.get(i).getMaxWeightKg());
                double bMin = nz(active.get(j).getMinWeightKg()), bMax = nz(active.get(j).getMaxWeightKg());
                if (aMin < bMax && bMin < aMax) {
                    throw new RuntimeException("Weight slabs must not overlap (kg ranges)");
                }
            }
        }
    }

    private static List<PaymentType> resolveAvailablePaymentTypes(Boolean codEnabled, Boolean onlineEnabled) {
        List<PaymentType> available = new ArrayList<>(2);
        if (Boolean.TRUE.equals(codEnabled)) {
            available.add(PaymentType.COD);
        }
        if (Boolean.TRUE.equals(onlineEnabled)) {
            available.add(PaymentType.ONLINE);
        }
        return available;
    }

    private static void validatePaymentToggleCombination(AppConfigEntity e) {
        List<PaymentType> available = resolveAvailablePaymentTypes(e.getCodEnabled(), e.getOnlineEnabled());
        if (available.isEmpty()) {
            throw new RuntimeException("At least one payment mode must remain enabled");
        }
        if (e.getDefaultPaymentType() != null && !available.contains(e.getDefaultPaymentType())) {
            throw new RuntimeException("defaultPaymentType must be one of enabled payment modes");
        }
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacyPlatformFeeInput(AppConfigEntity e, AppConfigDTO dto) {
        if (dto.getPlatformFee() == null) {
            return;
        }
        e.setPlatformFee(dto.getPlatformFee());
        if (dto.getIncityPlatformFee() == null) {
            e.setIncityPlatformFee(dto.getPlatformFee());
        }
        if (dto.getOutstationPlatformFee() == null) {
            e.setOutstationPlatformFee(dto.getPlatformFee());
        }
    }

    @SuppressWarnings("deprecation")
    private static void setDeprecatedLegacyPlatformFee(AppConfigDTO dto, Double legacy) {
        dto.setPlatformFee(legacy);
    }

    private void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}
