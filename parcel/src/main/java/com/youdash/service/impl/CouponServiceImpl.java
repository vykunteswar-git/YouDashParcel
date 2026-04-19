package com.youdash.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.coupon.CouponAdminResponseDTO;
import com.youdash.dto.coupon.CouponAdminUpsertDTO;
import com.youdash.dto.coupon.CouponApplication;
import com.youdash.dto.coupon.PublicCouponDTO;
import com.youdash.entity.CouponEntity;
import com.youdash.entity.CouponRedemptionEntity;
import com.youdash.model.CouponDiscountType;
import com.youdash.model.ServiceMode;
import com.youdash.repository.CouponRedemptionRepository;
import com.youdash.repository.CouponRepository;
import com.youdash.service.CouponService;

@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponRedemptionRepository couponRedemptionRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PublicCouponDTO>> listActiveVisibleForUser() {
        ApiResponse<List<PublicCouponDTO>> response = new ApiResponse<>();
        try {
            Instant now = Instant.now();
            List<CouponEntity> all = couponRepository.findByActiveTrueOrderByValidToAsc();
            List<PublicCouponDTO> list = all.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getActive()))
                    .filter(c -> !now.isBefore(c.getValidFrom()) && !now.isAfter(c.getValidTo()))
                    .filter(c -> c.getMaxRedemptionsTotal() == null
                            || c.getRedemptionCount() < c.getMaxRedemptionsTotal())
                    .map(this::toPublicDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CouponApplication resolveApplication(Long userId, String rawCode, double preCouponTotal, ServiceMode serviceMode) {
        if (userId == null) {
            throw new RuntimeException("User required for coupon");
        }
        String code = normalizeCode(rawCode);
        if (code == null) {
            throw new RuntimeException("couponCode is required");
        }
        CouponEntity c = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new RuntimeException("Invalid coupon code"));
        validateCouponEntity(c, userId, preCouponTotal, serviceMode, Instant.now());
        double disc = computeDiscountAmount(c, preCouponTotal);
        return new CouponApplication(c.getId(), c.getCode(), disc);
    }

    @Override
    @Transactional
    public void recordRedemption(long couponId, long userId, long orderId) {
        if (couponRedemptionRepository.existsByOrderId(orderId)) {
            return;
        }
        CouponEntity c = couponRepository.findById(couponId).orElseThrow();
        c.setRedemptionCount(c.getRedemptionCount() + 1);
        couponRepository.save(c);
        CouponRedemptionEntity r = new CouponRedemptionEntity();
        r.setCouponId(couponId);
        r.setUserId(userId);
        r.setOrderId(orderId);
        couponRedemptionRepository.save(r);
    }

    @Override
    @Transactional
    public ApiResponse<CouponAdminResponseDTO> adminCreate(CouponAdminUpsertDTO dto) {
        ApiResponse<CouponAdminResponseDTO> response = new ApiResponse<>();
        try {
            CouponEntity e = new CouponEntity();
            applyUpsert(e, dto, true);
            CouponEntity saved = couponRepository.save(e);
            response.setData(toAdminDto(saved));
            response.setMessage("Coupon created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setErr(response, ex.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public ApiResponse<CouponAdminResponseDTO> adminUpdate(long couponId, CouponAdminUpsertDTO dto) {
        ApiResponse<CouponAdminResponseDTO> response = new ApiResponse<>();
        try {
            CouponEntity e = couponRepository.findById(couponId)
                    .orElseThrow(() -> new RuntimeException("Coupon not found"));
            applyUpsert(e, dto, false);
            CouponEntity saved = couponRepository.save(e);
            response.setData(toAdminDto(saved));
            response.setMessage("Coupon updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setErr(response, ex.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<CouponAdminResponseDTO>> adminListAll() {
        ApiResponse<List<CouponAdminResponseDTO>> response = new ApiResponse<>();
        try {
            List<CouponAdminResponseDTO> list = couponRepository.findAll().stream()
                    .map(this::toAdminDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    private void validateCouponEntity(
            CouponEntity c,
            Long userId,
            double preCouponTotal,
            ServiceMode serviceMode,
            Instant now) {
        if (!Boolean.TRUE.equals(c.getActive())) {
            throw new RuntimeException("Coupon is not active");
        }
        if (now.isBefore(c.getValidFrom())) {
            throw new RuntimeException("Coupon is not valid yet");
        }
        if (now.isAfter(c.getValidTo())) {
            throw new RuntimeException("Coupon has expired");
        }
        if (c.getServiceMode() != null && serviceMode != null && c.getServiceMode() != serviceMode) {
            throw new RuntimeException("Coupon does not apply to this order type");
        }
        if (c.getMinOrderAmount() != null && preCouponTotal + 0.0001 < c.getMinOrderAmount()) {
            throw new RuntimeException("Order total below minimum for this coupon");
        }
        if (c.getMaxRedemptionsTotal() != null && c.getRedemptionCount() >= c.getMaxRedemptionsTotal()) {
            throw new RuntimeException("Coupon usage limit reached");
        }
        int perUser = c.getMaxRedemptionsPerUser() == null ? 1 : c.getMaxRedemptionsPerUser();
        long used = couponRedemptionRepository.countByCouponIdAndUserId(c.getId(), userId);
        if (used >= perUser) {
            throw new RuntimeException("You have already used this coupon");
        }
    }

    private static double computeDiscountAmount(CouponEntity c, double preCouponTotal) {
        if (preCouponTotal <= 0) {
            return 0.0;
        }
        CouponDiscountType t = c.getDiscountType();
        double v = nz(c.getDiscountValue());
        double raw;
        if (t == CouponDiscountType.PERCENT) {
            if (v < 0 || v > 100) {
                throw new RuntimeException("Invalid percent discount on coupon");
            }
            raw = preCouponTotal * (v / 100.0);
            if (c.getMaxDiscountAmount() != null) {
                raw = Math.min(raw, nz(c.getMaxDiscountAmount()));
            }
        } else if (t == CouponDiscountType.FLAT) {
            if (v < 0) {
                throw new RuntimeException("Invalid flat discount on coupon");
            }
            raw = v;
        } else {
            throw new RuntimeException("Unsupported discount type");
        }
        raw = Math.min(raw, preCouponTotal);
        return round2(raw);
    }

    private void applyUpsert(CouponEntity e, CouponAdminUpsertDTO dto, boolean isCreate) {
        if (isCreate) {
            String code = normalizeCode(dto.getCode());
            if (code == null) {
                throw new RuntimeException("code is required");
            }
            couponRepository.findByCodeIgnoreCase(code).ifPresent(other -> {
                throw new RuntimeException("Coupon code already exists");
            });
            e.setCode(code);
        } else if (StringUtils.hasText(dto.getCode())) {
            String code = normalizeCode(dto.getCode());
            if (code == null) {
                throw new RuntimeException("code is invalid");
            }
            if (!code.equalsIgnoreCase(e.getCode())) {
                couponRepository.findByCodeIgnoreCase(code).ifPresent(other -> {
                    if (!other.getId().equals(e.getId())) {
                        throw new RuntimeException("Coupon code already exists");
                    }
                });
                e.setCode(code);
            }
        }
        if (dto.getTitle() != null) {
            e.setTitle(dto.getTitle().trim());
        } else if (isCreate) {
            throw new RuntimeException("title is required");
        }
        if (dto.getDescription() != null) {
            e.setDescription(trimOrNull(dto.getDescription()));
        }
        if (dto.getDiscountType() != null) {
            e.setDiscountType(dto.getDiscountType());
        } else if (isCreate) {
            throw new RuntimeException("discountType is required");
        }
        if (dto.getDiscountValue() != null) {
            e.setDiscountValue(dto.getDiscountValue());
        } else if (isCreate) {
            throw new RuntimeException("discountValue is required");
        }
        if (dto.getMaxDiscountAmount() != null) {
            e.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        }
        if (dto.getMinOrderAmount() != null) {
            e.setMinOrderAmount(dto.getMinOrderAmount());
        }
        if (dto.getValidFrom() != null) {
            e.setValidFrom(dto.getValidFrom());
        } else if (isCreate) {
            throw new RuntimeException("validFrom is required");
        }
        if (dto.getValidTo() != null) {
            e.setValidTo(dto.getValidTo());
        } else if (isCreate) {
            throw new RuntimeException("validTo is required");
        }
        if (dto.getMaxRedemptionsTotal() != null) {
            e.setMaxRedemptionsTotal(dto.getMaxRedemptionsTotal());
        }
        if (dto.getMaxRedemptionsPerUser() != null) {
            e.setMaxRedemptionsPerUser(dto.getMaxRedemptionsPerUser());
        }
        if (dto.getServiceMode() != null) {
            e.setServiceMode(dto.getServiceMode());
        }
        if (dto.getActive() != null) {
            e.setActive(dto.getActive());
        }
        if (e.getValidFrom() != null && e.getValidTo() != null && !e.getValidTo().isAfter(e.getValidFrom())) {
            throw new RuntimeException("validTo must be after validFrom");
        }
    }

    private PublicCouponDTO toPublicDto(CouponEntity c) {
        return PublicCouponDTO.builder()
                .code(c.getCode())
                .title(c.getTitle())
                .description(c.getDescription())
                .discountSummary(buildDiscountSummary(c))
                .minOrderAmount(c.getMinOrderAmount())
                .serviceMode(c.getServiceMode())
                .validFrom(c.getValidFrom() != null ? c.getValidFrom().toString() : null)
                .validTo(c.getValidTo() != null ? c.getValidTo().toString() : null)
                .build();
    }

    private static String buildDiscountSummary(CouponEntity c) {
        if (c.getDiscountType() == CouponDiscountType.PERCENT) {
            String s = round2(nz(c.getDiscountValue())) + "% off";
            if (c.getMaxDiscountAmount() != null) {
                s += " (max " + round2(nz(c.getMaxDiscountAmount())) + ")";
            }
            return s;
        }
        return round2(nz(c.getDiscountValue())) + " off";
    }

    private CouponAdminResponseDTO toAdminDto(CouponEntity c) {
        return CouponAdminResponseDTO.builder()
                .id(c.getId())
                .code(c.getCode())
                .title(c.getTitle())
                .description(c.getDescription())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .maxDiscountAmount(c.getMaxDiscountAmount())
                .minOrderAmount(c.getMinOrderAmount())
                .validFrom(c.getValidFrom() != null ? c.getValidFrom().toString() : null)
                .validTo(c.getValidTo() != null ? c.getValidTo().toString() : null)
                .maxRedemptionsTotal(c.getMaxRedemptionsTotal())
                .redemptionCount(c.getRedemptionCount())
                .maxRedemptionsPerUser(c.getMaxRedemptionsPerUser())
                .serviceMode(c.getServiceMode())
                .active(c.getActive())
                .build();
    }

    private static String normalizeCode(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toUpperCase().replaceAll("\\s+", "");
        return t.isEmpty() ? null : t;
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static void setErr(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}
