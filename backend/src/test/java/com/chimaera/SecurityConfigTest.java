package com.chimaera;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void verifiesSupabaseTokenFromConfiguredLocalPublicKey() throws Exception {
        var issuer = "https://example.supabase.co/auth/v1";
        var key = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key")
                .algorithm(JWSAlgorithm.ES256)
                .generate();
        var decoder = new SecurityConfig().jwtDecoder(
                "https://example.supabase.co/auth/v1/.well-known/jwks.json",
                issuer,
                key.getKeyID(),
                key.getX().toString(),
                key.getY().toString());
        var token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject("member-123")
                        .issueTime(Date.from(Instant.now().minusSeconds(5)))
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .build());
        token.sign(new ECDSASigner(key.toECPrivateKey()));

        var decoded = decoder.decode(token.serialize());

        assertThat(decoded.getSubject()).isEqualTo("member-123");
        assertThat(decoded.getIssuer().toString()).isEqualTo(issuer);
    }
}
