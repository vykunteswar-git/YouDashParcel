package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.BannerDTO;
import com.youdash.dto.BannerRequestDTO;
import com.youdash.entity.BannerEntity;
import com.youdash.repository.BannerRepository;
import com.youdash.service.BannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BannerServiceImpl implements BannerService {

    @Autowired
    private BannerRepository bannerRepository;

    @Override
    public ApiResponse<List<BannerDTO>> listPublicActive() {
        ApiResponse<List<BannerDTO>> response = new ApiResponse<>();
        try {
            List<BannerDTO> list = bannerRepository
                    .findByIsActiveTrue(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))).stream()
                    .filter(this::hasValidWindowNow)
                    .map(this::toDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<BannerDTO>> listAdmin() {
        ApiResponse<List<BannerDTO>> response = new ApiResponse<>();
        try {
            List<BannerDTO> list = bannerRepository
                    .findAll(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))).stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<BannerDTO> createAdmin(BannerRequestDTO dto) {
        ApiResponse<BannerDTO> response = new ApiResponse<>();
        try {
            validateRequest(dto, true);
            BannerEntity e = new BannerEntity();
            applyRequest(e, dto, true);
            BannerEntity saved = bannerRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Banner created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<BannerDTO> updateAdmin(Long id, BannerRequestDTO dto) {
        ApiResponse<BannerDTO> response = new ApiResponse<>();
        try {
            BannerEntity e = bannerRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Banner not found"));
            validateRequest(dto, false);
            applyRequest(e, dto, false);
            BannerEntity saved = bannerRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Banner updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<Void> deleteAdmin(Long id) {
        ApiResponse<Void> response = new ApiResponse<>();
        try {
            if (!bannerRepository.existsById(Objects.requireNonNull(id))) {
                throw new RuntimeException("Banner not found");
            }
            bannerRepository.deleteById(id);
            response.setMessage("Banner deleted");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    private boolean hasValidWindowNow(BannerEntity e) {
        Instant now = Instant.now();
        if (e.getStartsAt() != null && now.isBefore(e.getStartsAt())) {
            return false;
        }
        return e.getEndsAt() == null || !now.isAfter(e.getEndsAt());
    }

    private void validateRequest(BannerRequestDTO dto, boolean create) {
        if (create && (dto.getImageUrl() == null || dto.getImageUrl().isBlank())) {
            throw new RuntimeException("imageUrl is required");
        }
        Instant startsAt = parseInstant(dto.getStartsAt(), "startsAt");
        Instant endsAt = parseInstant(dto.getEndsAt(), "endsAt");
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new RuntimeException("endsAt must be after startsAt");
        }
    }

    private void applyRequest(BannerEntity e, BannerRequestDTO dto, boolean create) {
        if (dto.getTitle() != null) {
            e.setTitle(trimToNull(dto.getTitle()));
        }
        if (dto.getSubtitle() != null) {
            e.setSubtitle(trimToNull(dto.getSubtitle()));
        }
        if (dto.getImageUrl() != null) {
            String image = trimToNull(dto.getImageUrl());
            if (image == null && create) {
                throw new RuntimeException("imageUrl is required");
            }
            if (image != null) {
                e.setImageUrl(image);
            }
        }
        if (dto.getRedirectUrl() != null) {
            e.setRedirectUrl(trimToNull(dto.getRedirectUrl()));
        }
        if (dto.getSortOrder() != null) {
            e.setSortOrder(dto.getSortOrder());
        }
        if (dto.getIsActive() != null) {
            e.setIsActive(dto.getIsActive());
        }
        if (dto.getStartsAt() != null) {
            e.setStartsAt(parseInstant(dto.getStartsAt(), "startsAt"));
        }
        if (dto.getEndsAt() != null) {
            e.setEndsAt(parseInstant(dto.getEndsAt(), "endsAt"));
        }
    }

    private static Instant parseInstant(String raw, String field) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ex) {
            throw new RuntimeException(field + " must be ISO-8601 UTC timestamp");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private BannerDTO toDto(BannerEntity e) {
        return BannerDTO.builder()
                .id(e.getId())
                .title(e.getTitle())
                .subtitle(e.getSubtitle())
                .imageUrl(e.getImageUrl())
                .redirectUrl(e.getRedirectUrl())
                .sortOrder(e.getSortOrder())
                .isActive(e.getIsActive())
                .startsAt(e.getStartsAt() != null ? e.getStartsAt().toString() : null)
                .endsAt(e.getEndsAt() != null ? e.getEndsAt().toString() : null)
                .build();
    }

    private static void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}
