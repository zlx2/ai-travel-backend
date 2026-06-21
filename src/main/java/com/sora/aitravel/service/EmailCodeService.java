package com.sora.aitravel.service;

public interface EmailCodeService {
    void send(String email, String scene);

    void verify(String email, String scene, String code);

    void remove(String email, String scene);
}
