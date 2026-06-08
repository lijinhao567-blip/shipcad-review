package com.shipcad.review.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EvidenceLocationConverter implements AttributeConverter<EvidenceLocation, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(EvidenceLocation attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Evidence location could not be serialized", exception);
        }
    }

    @Override
    public EvidenceLocation convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, EvidenceLocation.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Evidence location could not be parsed", exception);
        }
    }
}
