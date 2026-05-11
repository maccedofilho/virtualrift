package com.virtualrift.auth.model;

public enum OAuthProvider {
    GITHUB,
    GOOGLE;

    public String wireValue() {
        return name().toLowerCase();
    }
}
