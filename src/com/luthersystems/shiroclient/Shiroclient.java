package com.luthersystems.shiroclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.UUID;

public class Shiroclient {

    private static final Logger logger = LoggerFactory.getLogger(Shiroclient.class);

    private HttpClient httpClient;
    private RequestOptions.RequestOption[] baseOptions;

    public Shiroclient (RequestOptions.RequestOption ... baseOptions) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseOptions = baseOptions;
    }

    private RequestOptions applyOptions(RequestOptions.RequestOption[] opts) {
        var uuid = UUID.randomUUID();

        var config = new RequestOptions();
        config.id = uuid.toString();

        for (var opt : baseOptions) {
           opt.apply(config);
        }

        for (var opt : opts) {
            opt.apply(config);
        }

        return config;
    }

    public ShiroResponse call(String method, RequestOptions.RequestOption ... opts) throws Exception {

        var config = applyOptions(opts);

        var transientJson = new HashMap<String, Object>();
        config.transientData.forEach((key, value) -> {
            transientJson.put(key, Hex.encodeHexString(value));
        });

        if (config.timestampGenerator != null) {
            var ts = config.timestampGenerator.make();
            transientJson.put("timestamp_override", Hex.encodeHexString(ts.getBytes()));
        }

        var params = new HashMap<>() {{
            put("method", method);
            put("params", method);
        }};


        /*
        params := map[string]interface{}{
            "method":    method,
                    "params":    opt.params,
                    "transient": transientJSON,
        }
         */


        logger.info("Message to log");

        var values = new HashMap<>() {{
            put("name", "John Doe");
            put ("occupation", "gardener");
        }};

        var objectMapper = new ObjectMapper();
        var requestBody = objectMapper
                .writeValueAsString(values);

        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/post"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body());

        return new ShiroResponse();
    }
}
