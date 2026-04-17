package com.youdash.security;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.youdash.entity.RiderEntity;
import com.youdash.entity.UserEntity;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RiderAccessVerifier {

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Admin may access any rider. Otherwise token subject must match rider id, or a USER with the same phone as the rider.
     */
    public boolean canAccessRider(HttpServletRequest request, Long riderId) {
        if (riderId == null) {
            return false;
        }
        String type = (String) request.getAttribute("type");
        Long uid = (Long) request.getAttribute("userId");
        if ("ADMIN".equals(type)) {
            return true;
        }
        if ("RIDER".equals(type) && uid != null && uid.equals(riderId)) {
            return true;
        }
        if (uid != null && uid.equals(riderId)) {
            return true;
        }
        if ("USER".equals(type) && uid != null) {
            UserEntity user = userRepository.findById(uid).orElse(null);
            RiderEntity rider = riderRepository.findById(riderId).orElse(null);
            if (user == null || rider == null || user.getPhoneNumber() == null || rider.getPhone() == null) {
                return false;
            }
            return Objects.equals(user.getPhoneNumber().trim(), rider.getPhone().trim());
        }
        return false;
    }

    /**
     * Resolve the acting rider based on JWT {@code type} + {@code userId}.
     * <ul>
     *   <li>{@code RIDER} token → {@code userId} is the rider PK.</li>
     *   <li>{@code USER} token → look up user, then find rider by matching phone.</li>
     * </ul>
     */
    public Long resolveActingRiderId(HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("userId");
        String type = (String) request.getAttribute("type");
        if (uid == null) {
            throw new RuntimeException("Unauthorized");
        }

        if ("RIDER".equals(type)) {
            if (!riderRepository.existsById(uid)) {
                throw new RuntimeException("Rider not found for this token (id=" + uid + ")");
            }
            return uid;
        }

        if ("USER".equals(type)) {
            UserEntity user = userRepository.findById(uid)
                    .orElseThrow(() -> new RuntimeException("User not found for this token (id=" + uid + ")"));
            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                throw new RuntimeException("Rider profile not found for this account");
            }
            return riderRepository.findByPhone(user.getPhoneNumber().trim())
                    .map(RiderEntity::getId)
                    .orElseThrow(() -> new RuntimeException("Rider profile not found for this account"));
        }

        // Legacy fallback: no type on token, infer from tables.
        if (riderRepository.findById(uid).isPresent()) {
            return uid;
        }
        UserEntity user = userRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("No rider or user found for id=" + uid));
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            throw new RuntimeException("Rider profile not found for this account");
        }
        return riderRepository.findByPhone(user.getPhoneNumber().trim())
                .map(RiderEntity::getId)
                .orElseThrow(() -> new RuntimeException("Rider profile not found for this account"));
    }

    /**
     * Resolve rider PK from JWT subject + token type without an {@link HttpServletRequest}.
     */
    public Long resolveActingRiderIdFromToken(Long tokenUserId, String tokenType) {
        if (tokenUserId == null) {
            throw new RuntimeException("Unauthorized");
        }
        if ("RIDER".equals(tokenType)) {
            if (!riderRepository.existsById(tokenUserId)) {
                throw new RuntimeException("Unauthorized");
            }
            return tokenUserId;
        }
        if ("USER".equals(tokenType)) {
            UserEntity user = userRepository.findById(tokenUserId)
                    .orElseThrow(() -> new RuntimeException("Unauthorized"));
            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                throw new RuntimeException("Rider profile not found for this account");
            }
            return riderRepository.findByPhone(user.getPhoneNumber().trim())
                    .map(RiderEntity::getId)
                    .orElseThrow(() -> new RuntimeException("Rider profile not found for this account"));
        }
        throw new RuntimeException("Unauthorized");
    }
}
