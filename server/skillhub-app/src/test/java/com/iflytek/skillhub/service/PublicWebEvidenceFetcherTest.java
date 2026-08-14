package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class PublicWebEvidenceFetcherTest {
    @Test
    void acceptsAStandardPublicHttpsAddressWithoutResolvingItInThisUnitTest() {
        assertThat(PublicWebEvidenceFetcher.parseAndValidate("https://docs.example.com/guide", true).getHost())
                .isEqualTo("docs.example.com");
    }

    @Test
    void rejectsPrivateAddressShapesAndNonStandardPorts() {
        assertThatThrownBy(() -> PublicWebEvidenceFetcher.parseAndValidate("http://localhost:8080", true))
                .isInstanceOf(CatalogDomainException.class);
        assertThatThrownBy(() -> PublicWebEvidenceFetcher.parseAndValidate("https://example.com:8080/docs", true))
                .isInstanceOf(CatalogDomainException.class);
        assertThatThrownBy(() -> PublicWebEvidenceFetcher.parseAndValidate("file:///tmp/readme", true))
                .isInstanceOf(CatalogDomainException.class);
    }

    @Test
    void recognisesPrivateAndPublicNetworkAddresses() throws Exception {
        assertThat(PublicWebEvidenceFetcher.isPublic(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(PublicWebEvidenceFetcher.isPublic(InetAddress.getByName("10.0.0.1"))).isFalse();
        assertThat(PublicWebEvidenceFetcher.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
    }
}
