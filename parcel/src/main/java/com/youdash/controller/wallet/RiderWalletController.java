package com.youdash.controller.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.wallet.RiderWalletSummaryDTO;
import com.youdash.dto.wallet.RiderWalletTransactionDTO;
import com.youdash.dto.wallet.RiderWithdrawalDTO;
import com.youdash.dto.wallet.RiderWithdrawalRequestDTO;
import com.youdash.dto.incentive.RiderIncentiveProgressDTO;
import com.youdash.security.RiderAccessVerifier;
import com.youdash.service.OrderService;
import com.youdash.service.PeakIncentiveService;
import com.youdash.service.wallet.RiderWalletService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/rider")
@Tag(name = "Rider — Wallet & orders", description = "Rider JWT: wallet, withdrawals, and order list/detail.")
public class RiderWalletController {

    @Autowired
    private RiderWalletService riderWalletService;

    @Autowired
    private RiderAccessVerifier riderAccessVerifier;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PeakIncentiveService peakIncentiveService;

    @GetMapping("/wallet")
    public ApiResponse<RiderWalletSummaryDTO> wallet(HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderWalletService.getWalletSummary(riderId);
    }

    @GetMapping("/transactions")
    public ApiResponse<List<RiderWalletTransactionDTO>> transactions(
            HttpServletRequest request,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderWalletService.listTransactions(riderId, page, size);
    }

    @PostMapping("/withdraw")
    public ApiResponse<RiderWithdrawalDTO> withdraw(@RequestBody RiderWithdrawalRequestDTO dto, HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderWalletService.requestWithdrawal(riderId, dto);
    }

    @GetMapping("/withdrawals")
    public ApiResponse<List<RiderWithdrawalDTO>> withdrawals(
            HttpServletRequest request,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderWalletService.listWithdrawals(riderId, page, size);
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderResponseDTO>> myOrders(HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return orderService.listRiderOrders(riderId);
    }

    @GetMapping("/incentives/progress")
    @Operation(summary = "Get active incentive progress (JWT)")
    public ApiResponse<List<RiderIncentiveProgressDTO>> incentiveProgress(HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return peakIncentiveService.riderProgress(riderId);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(
            summary = "Get order by id",
            description = "Returns the order only if it is assigned to the authenticated rider (same as GET /orders/{id} with RIDER token). "
                    + "On business errors, `success` is false and `message` explains (HTTP may still be 200).",
            parameters = {
                    @Parameter(name = "orderId", description = "Internal order id (same as socket orderId)", example = "64")
            },
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Wrapped ApiResponse; see examples",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = {
                                            @ExampleObject(
                                                    name = "success",
                                                    summary = "INCITY order assigned to this rider",
                                                    value = """
                                                            {
                                                              "data": {
                                                                "id": 64,
                                                                "userId": 6,
                                                                "categoryId": 1,
                                                                "senderName": "akhik",
                                                                "senderPhone": "9846466464",
                                                                "receiverName": "devi",
                                                                "receiverPhone": "9846461691",
                                                                "imageUrl": "https://res.cloudinary.com/example/image.jpg",
                                                                "pickupLat": 17.71226537958464,
                                                                "pickupLng": 83.2028441503644,
                                                                "dropLat": 17.712228011790454,
                                                                "dropLng": 83.29742588102818,
                                                                "serviceMode": "INCITY",
                                                                "vehicleId": 3,
                                                                "weight": 5.0,
                                                                "distanceKm": 13.374,
                                                                "paymentType": "ONLINE",
                                                                "status": "CONFIRMED",
                                                                "riderId": 18,
                                                                "riderName": "Rider Name",
                                                                "riderPhone": "9876543210",
                                                                "deliveryOtp": "482193",
                                                                "isOtpVerified": false,
                                                                "totalAmount": 255.01,
                                                                "displayOrderId": "YP-641776511148564",
                                                                "paymentStatus": "PAID",
                                                                "createdAt": "2025-04-18T16:49:10.123456Z"
                                                              },
                                                              "message": "OK",
                                                              "messageKey": "SUCCESS",
                                                              "status": 200,
                                                              "success": true
                                                            }"""),
                                            @ExampleObject(
                                                    name = "accessDenied",
                                                    summary = "Order not assigned to this rider",
                                                    value = """
                                                            {
                                                              "data": null,
                                                              "message": "Access denied",
                                                              "messageKey": "ERROR",
                                                              "status": 500,
                                                              "success": false
                                                            }""")
                                    }))
            })
    public ApiResponse<OrderResponseDTO> getOrderById(
            @PathVariable Long orderId,
            HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return orderService.getOrder(orderId, riderId, "RIDER", false);
    }
}
