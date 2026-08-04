package com.iflytek.skillhub.catalog.domain;

/** Stable error contract exported by the Catalog bounded context. */
public class CatalogDomainException extends RuntimeException {
    private final String code;
    private final int status;
    private final Object[] arguments;

    public CatalogDomainException(String code, int status, Object... arguments) {
        super(code);
        this.code = code;
        this.status = status;
        this.arguments = arguments != null ? arguments.clone() : new Object[0];
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public Object[] arguments() {
        return arguments.clone();
    }

    public static CatalogDomainException badRequest(String code, Object... arguments) {
        return new CatalogDomainException(code, 400, arguments);
    }

    public static CatalogDomainException forbidden(String code, Object... arguments) {
        return new CatalogDomainException(code, 403, arguments);
    }

    public static CatalogDomainException notFound(String code, Object... arguments) {
        return new CatalogDomainException(code, 404, arguments);
    }

    public static CatalogDomainException conflict(String code, Object... arguments) {
        return new CatalogDomainException(code, 409, arguments);
    }
}
