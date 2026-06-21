package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.AccessControlList;
import com.qcloud.cos.region.Region;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = ExternalIntegrationTestSupport.ENABLE_PROPERTY, matches = "true")
class CosIntegrationTest extends ExternalIntegrationTestSupport {

    @Test
    void cosCredentialsCanReadConfiguredBucket() {
        COSCredentials credentials =
                new BasicCOSCredentials(
                        requiredEnv("COS_SECRET_ID"), requiredEnv("COS_SECRET_KEY"));
        ClientConfig config = new ClientConfig(new Region(requiredEnv("COS_REGION")));
        COSClient client = new COSClient(credentials, config);

        try {
            AccessControlList acl = client.getBucketAcl(requiredEnv("COS_BUCKET"));
            assertThat(acl).isNotNull();
            assertThat(URI.create(requiredEnv("COS_DOMAIN")).getScheme()).isIn("http", "https");
        } finally {
            client.shutdown();
        }
    }
}
