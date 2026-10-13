package com.medcore.api.patient;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

public enum BloodType {
    A_POSITIVE("A+"), A_NEGATIVE("A-"), B_POSITIVE("B+"), B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"), AB_NEGATIVE("AB-"), O_POSITIVE("O+"), O_NEGATIVE("O-");

    private final String code;

    BloodType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static BloodType fromCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown blood type " + code));
    }

    /** Persists the clinical notation (e.g. "AB+") instead of the Java constant name. */
    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<BloodType, String> {

        @Override
        public String convertToDatabaseColumn(BloodType attribute) {
            return attribute == null ? null : attribute.code;
        }

        @Override
        public BloodType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : fromCode(dbData);
        }
    }
}
