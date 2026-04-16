package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class RiderCommissionConfigDTO {
    private Double onlineCommissionPercent;
    private Double codCashCommissionPercent;
    private Double codQrCommissionPercent;
    private Double peakSurgeBonusFlat;
    private Double baseFee;
    private Double perKmRate;
}
