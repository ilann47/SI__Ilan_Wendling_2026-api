package com.kaneko.estacionamento.support.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.kaneko.estacionamento.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@TenantApiIntegrationTest
class TenantApiIntegrationTestTest {

    @Autowired
    private TenantTestSupport tenant;

    @Autowired
    private JwtService jwtService;

    @Test
    void criaTenantEEmiteContextoPelosEndpointsReais() throws Exception {
        TenantFixture fixture = tenant.criarTenantAdmin();

        JwtService.Claims claims = jwtService.validar(tenant.tokenSemBearer(fixture.tokenContextual()));

        assertThat(claims.organizacaoId()).isEqualTo(fixture.organizacaoId());
        assertThat(claims.membershipId()).isEqualTo(fixture.membershipId());
        assertThat(claims.membershipVersion()).isZero();
        assertThat(fixture.tokenGlobal()).startsWith("Bearer ");
        assertThat(fixture.tokenContextual()).startsWith("Bearer ");
    }
}
