package com.g3cs.integration.service;

public interface SequenceGeneratorService {
    String generateNextNumber(String category);
    String generateNextNumber(String category, String parentCategory);
}
