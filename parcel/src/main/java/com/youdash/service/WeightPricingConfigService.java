package com.youdash.service;

import com.youdash.dto.WeightPricingConfigDTO;

public interface WeightPricingConfigService {

    WeightPricingConfigDTO getActive();

    WeightPricingConfigDTO create(WeightPricingConfigDTO dto);

    WeightPricingConfigDTO update(WeightPricingConfigDTO dto);
}
