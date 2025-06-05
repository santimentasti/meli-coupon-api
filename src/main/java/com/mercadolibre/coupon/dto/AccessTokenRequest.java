package com.mercadolibre.coupon.dto;

public class AccessTokenRequest {
    private String code;
    private String refreshToken;

    public AccessTokenRequest() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
