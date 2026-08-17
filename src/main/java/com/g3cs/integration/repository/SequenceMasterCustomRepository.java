package com.g3cs.integration.repository;

import com.g3cs.integration.model.SequenceMaster;

public interface SequenceMasterCustomRepository {
    SequenceMaster getTemplate(String category);
    SequenceMaster getNextGlobalSequence(String category);
}
