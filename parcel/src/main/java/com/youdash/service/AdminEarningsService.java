package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminEarningsDTO;

public interface AdminEarningsService {
    ApiResponse<AdminEarningsDTO> getEarnings(String range);
}
