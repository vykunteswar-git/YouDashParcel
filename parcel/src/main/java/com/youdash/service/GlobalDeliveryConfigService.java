package com.youdash.service;

import com.youdash.dto.GlobalDeliveryConfigDTO;

public interface GlobalDeliveryConfigService {

    GlobalDeliveryConfigDTO getActive();

    GlobalDeliveryConfigDTO create(GlobalDeliveryConfigDTO dto);

    GlobalDeliveryConfigDTO update(GlobalDeliveryConfigDTO dto);
}
