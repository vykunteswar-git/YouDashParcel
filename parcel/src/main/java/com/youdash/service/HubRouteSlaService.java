package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRouteSLARequestDTO;
import com.youdash.dto.HubRouteSLAResponseDTO;
import com.youdash.dto.HubRouteSlaPreviewResponseDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface HubRouteSlaService {

    ApiResponse<HubRouteSLAResponseDTO> create(HubRouteSLARequestDTO dto);

    ApiResponse<List<HubRouteSLAResponseDTO>> listByHubRouteId(Long hubRouteId);

    ApiResponse<HubRouteSLAResponseDTO> update(Long id, HubRouteSLARequestDTO dto);

    LocalDateTime calculateDelivery(Long hubRouteId);

    ApiResponse<HubRouteSlaPreviewResponseDTO> preview(Long hubRouteId);
}
