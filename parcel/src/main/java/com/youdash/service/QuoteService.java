package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.QuoteRequestDTO;
import com.youdash.dto.QuoteResponseDTO;

public interface QuoteService {

    ApiResponse<QuoteResponseDTO> quote(QuoteRequestDTO dto);
}
