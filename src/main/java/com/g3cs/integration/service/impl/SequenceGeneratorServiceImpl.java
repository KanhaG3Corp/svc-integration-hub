package com.g3cs.integration.service.impl;

import com.g3cs.integration.model.SequenceCounter;
import com.g3cs.integration.model.SequenceMaster;
import com.g3cs.integration.repository.SequenceCounterCustomRepository;
import com.g3cs.integration.repository.SequenceMasterCustomRepository;
import com.g3cs.integration.service.SequenceGeneratorService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SequenceGeneratorServiceImpl implements SequenceGeneratorService {

    private final SequenceMasterCustomRepository masterRepository;
    private final SequenceCounterCustomRepository counterRepository;

    public SequenceGeneratorServiceImpl(SequenceMasterCustomRepository masterRepository,
                                        SequenceCounterCustomRepository counterRepository) {
        this.masterRepository = masterRepository;
        this.counterRepository = counterRepository;
    }

    @Override
    public String generateNextNumber(String category) {
        return generateNextNumber(category, null);
    }

    @Override
    public String generateNextNumber(String category, String parentCategory) {
        if (!StringUtils.hasText(category)) {
            throw new IllegalArgumentException("Category must not be null or empty");
        }
        if (!StringUtils.hasText(parentCategory)) {
            SequenceMaster globalSeq = masterRepository.getNextGlobalSequence(category);
            if (globalSeq == null) {
                throw new IllegalStateException("Sequence not configured or inactive for category: " + category);
            }
            return buildFinalCode(globalSeq.getPrefix(), globalSeq.getSuffix(),
                    globalSeq.getNumberOfDigits(), globalSeq.getLastNumber());
        }
        SequenceMaster template = masterRepository.getTemplate(category);
        if (template == null) {
            throw new IllegalStateException("Sequence template not configured or inactive for category: " + category);
        }
        SequenceCounter counter = counterRepository.getNextSequenceCounter(category, parentCategory);
        return buildFinalCode(template.getPrefix(), template.getSuffix(),
                template.getNumberOfDigits(), counter.getLastNumber());
    }

    private String buildFinalCode(String prefix, String suffix, Integer digits, Long number) {
        if (digits == null || digits <= 0) {
            throw new IllegalStateException("Invalid digits configuration");
        }
        String numberPart = String.format("%0" + digits + "d", number);
        return (prefix != null ? prefix : "") + numberPart + (suffix != null ? suffix : "");
    }
}
