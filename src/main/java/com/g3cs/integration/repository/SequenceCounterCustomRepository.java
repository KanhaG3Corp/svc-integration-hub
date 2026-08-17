package com.g3cs.integration.repository;

import com.g3cs.integration.model.SequenceCounter;

public interface SequenceCounterCustomRepository {
    SequenceCounter getNextSequenceCounter(String category, String parentCategory);
}
