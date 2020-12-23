package com.luthersystems.shiroclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

class ShiroClientTest {

    public static class HealthCheckReport {
        public String serviceName;
        public String serviceVersion;
        public String status;
        public String timestamp;

        @JsonCreator
        public HealthCheckReport(
                @JsonProperty("service_name") String serviceName,
                @JsonProperty("service_version") String serviceVersion,
                @JsonProperty("status") String status,
                @JsonProperty("timestamp") String timestamp
        ) {
            this.serviceName = serviceName;
            this.serviceVersion = serviceVersion;
            this.status = status;
            this.timestamp = timestamp;
        }
    }

    public static class HealthCheckResponse {
        public HealthCheckReport[] reports;
        @JsonCreator
        public HealthCheckResponse(
                @JsonProperty("reports") HealthCheckReport[] reports
        ) {
            this.reports = reports;
        }
    }

    @Test
    void healthcheck() throws Exception {
        var endpoint ="http://127.0.0.1:8082";
        var res = new ShiroClient(
                ShiroClient.WithEndpoint(endpoint),
                ShiroClient.WithCreator("martin")
        ).call("healthcheck",
                ShiroClient.WithParams(new HashMap<String, Object>()),
                ShiroClient.WithTransientDataMap(new HashMap<>())
        );
        System.out.println(new String(res.resultJson()));
        var result = res.unmarshal(HealthCheckResponse.class);
        assertEquals(1, result.reports.length);
        assertEquals("UP", result.reports[0].status);
    }

}
