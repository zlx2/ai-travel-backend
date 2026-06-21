package com.sora.aitravel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sora.aitravel.dto.request.RegisterRequest;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.service.EmailCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private SysUserMapper userMapper;
    @Mock private EmailCodeService emailCodeService;

    @Test
    void registerShouldFollowFinalSpecification() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class)))
                .thenAnswer(
                        invocation -> {
                            invocation.<SysUser>getArgument(0).setId(7L);
                            return 1;
                        });
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthServiceImpl service = new AuthServiceImpl(userMapper, emailCodeService, encoder);

        Long id =
                service.register(
                        new RegisterRequest("sora", "123456", "TEST@example.com", "654321"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertThat(id).isEqualTo(7L);
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getNickname()).isEqualTo("sora");
        assertThat(saved.getRole()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getDeleted()).isZero();
        assertThat(saved.getPasswordHash()).isNotEqualTo("123456");
        assertThat(encoder.matches("123456", saved.getPasswordHash())).isTrue();
        verify(emailCodeService).verify("test@example.com", "register", "654321");
        verify(emailCodeService).remove("test@example.com", "register");
    }
}
