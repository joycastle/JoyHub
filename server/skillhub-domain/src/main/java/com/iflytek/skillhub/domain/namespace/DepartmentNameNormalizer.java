package com.iflytek.skillhub.domain.namespace;

import java.util.Locale;

/** Normalizes Feishu directory labels to their canonical JoyHub department names. */
public final class DepartmentNameNormalizer {
    private DepartmentNameNormalizer() {
    }

    public static String normalize(String displayName) {
        String chineseName = displayName == null ? "" : displayName.split("\\|", 2)[0].trim();
        return chineseName.replaceFirst("^部门\\d+\\s*", "").trim().toLowerCase(Locale.ROOT);
    }
}
