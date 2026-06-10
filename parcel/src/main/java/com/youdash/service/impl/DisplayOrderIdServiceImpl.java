package com.youdash.service.impl;

import com.youdash.entity.DisplayOrderSequenceEntity;
import com.youdash.repository.DisplayOrderSequenceRepository;
import com.youdash.service.DisplayOrderIdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisplayOrderIdServiceImpl implements DisplayOrderIdService {

    @Value("${youdash.display-order-id.sequence-start:1000}")
    private long sequenceStart;

    @Autowired
    private DisplayOrderSequenceRepository displayOrderSequenceRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String allocateNext() {
        DisplayOrderSequenceEntity row = displayOrderSequenceRepository
                .findByIdForUpdate(DisplayOrderSequenceEntity.SINGLETON_ID)
                .orElseGet(this::createInitialRow);
        long assigned = row.getNextValue();
        row.setNextValue(assigned + 1);
        displayOrderSequenceRepository.save(row);
        return "YP-" + assigned;
    }

    private DisplayOrderSequenceEntity createInitialRow() {
        DisplayOrderSequenceEntity row = new DisplayOrderSequenceEntity();
        row.setId(DisplayOrderSequenceEntity.SINGLETON_ID);
        row.setNextValue(Math.max(1L, sequenceStart));
        return displayOrderSequenceRepository.save(row);
    }
}
