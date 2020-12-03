package com.luthersystems.shiroclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.rmi.UnexpectedException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShiroClient {

    private static final Logger logger = LoggerFactory.getLogger(ShiroClient.class);

    private static final String METHOD_CALL = "Call";

    private static final int ERROR_LEVEL_NO_ERROR  = 0;
    private static final int ERROR_LEVEL_SHIROCLIENT  = 1;
    private static final int ERROR_LEVEL_PHYLUM  = 2;
    private static final int ERROR_CODE_SHIROCLIENT_NONE  = 0;
    private static final int ERROR_CODE_SHIROCLIENT_TIMEOUT  = 1;

    private final HttpClient httpClient;
    private final RequestOption[] baseOptions;

    public ShiroClient(RequestOption ... baseOptions) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseOptions = baseOptions;
    }

    private static class RequestOptions {
        Map<String, String> headers;
        String endpoint;
        String id;
        String authToken;
        Object params;
        Map<String, byte[]> transientData;
        // target: leave out target/response
        String[] mspFilter;
        int minEndorsers;
        String creator;
        String dependentTxId;
        boolean disableWritePolling;
        boolean ccFetchUrlDowngrade;
        String ccFetchUrlProxy;
        TimestampGenerator timestampGenerator;
    }

    public interface RequestOption {
        void apply(RequestOptions opts);
    }

    public interface TimestampGenerator {
        String make();
    }

    public static RequestOption WithHeader(String key, String val) {
        return opts -> {
            if (opts.headers == null) {
                opts.headers = new HashMap<>();
            }
            opts.headers.put(key, val);
        };
    }

    public static RequestOption WithEndpoint(String endpoint) {
        return opts -> opts.endpoint = endpoint;
    }

    public static RequestOption WithId(String id) {
        return opts -> opts.id = id;
    }

    public static RequestOption WithParams(Object params) {
        return opts -> opts.params = params;
    }

    public static RequestOption WithAuthToken(String authToken) {
        return opts -> opts.authToken = authToken;
    }

    public static RequestOption WithTimestampGenerator(TimestampGenerator timestampGenerator) {
        return opts -> opts.timestampGenerator = timestampGenerator;
    }

    public static RequestOption WithTransientDataMap(Map<String, byte[]> data) {
        return opts -> {
            if (data == null) {
                return;
            }
            if (opts.transientData == null) {
                opts.transientData = new HashMap<>();
            }
            data.forEach((key, value) -> opts.transientData.put(key, value));
        };
    }

    public static RequestOption WithMspFilter(String[] mspFilter) {
        return opts -> {
            if (mspFilter == null) {
                return;
            }
            opts.mspFilter = Arrays.copyOf(mspFilter, mspFilter.length);
        };
    }

    public static RequestOption WithTransientData(String key, byte[] val) {
        return opts -> {
            if (opts.transientData == null) {
                opts.transientData = new HashMap<>();
            }
            opts.transientData.put(key, val);
        };
    }

    public static RequestOption WithMinEndorsers(int minEndorsers) {
        return opts -> opts.minEndorsers = minEndorsers;
    }

    public static RequestOption WithCreator(String creator) {
        return opts -> opts.creator = creator;
    }

    public static RequestOption WithDependentTxId(String dependentTxId) {
        return opts -> opts.dependentTxId = dependentTxId;
    }

    public static RequestOption WithDisableWritePolling(boolean disableWritePolling) {
        return opts -> opts.disableWritePolling = disableWritePolling;
    }

    public static RequestOption WithCcFetchUrlDowngrade(boolean ccFetchUrlDowngrade) {
        return opts -> opts.ccFetchUrlDowngrade = ccFetchUrlDowngrade;
    }

    public static RequestOption WithCcFetchUrlProxy(String ccFetchUrlProxy) {
        return opts -> opts.ccFetchUrlProxy = ccFetchUrlProxy;
    }

    private RequestOptions applyOptions(RequestOption[] opts) {
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

    private class SmartContractError extends Exception {
        int code;

        SmartContractError(String message) {
            super(message);
            this.code = ERROR_CODE_SHIROCLIENT_NONE;
        }

        SmartContractError(String message, int code) {
            super(message);
            this.code = code;
        }
    }

    static boolean isTimeoutError(Exception e) {
        if (!(e instanceof SmartContractError)) {
            return false;
        }
        return ((SmartContractError) e).code == ERROR_CODE_SHIROCLIENT_TIMEOUT;
    }

    private class RpcRes {
        int errorLevel;
        Object result;
        Object code;
        Object message;
        Object data;
        String txId;

        RpcRes(int errorLevel, Object result, Object code, Object message, Object data, String txId) {
            this.errorLevel = errorLevel;
            this.result = result;
            this.code = code;
            this.message = message;
            this.data = data;
            this.txId = txId;
        }

        boolean isShiroClientError() {
           return this.errorLevel == ERROR_LEVEL_SHIROCLIENT;
        }

        Exception getShiroClientError() {
            if (message == null || !(message instanceof String)) {
               return new SmartContractError("shiroclient error with no message");
            }
            var intCode = 0;
            if (code != null && (code instanceof Integer)) {
               intCode = (Integer) code;
            }
            return new SmartContractError((String) message, intCode);
        }
    }

    private RpcRes reqres(Object req, RequestOptions config) throws Exception {
        if (req == null) {
           throw new IllegalArgumentException("missing request");
        }
        if (config == null) {
            throw new IllegalArgumentException("missing config");
        }
        var objectMapper = new ObjectMapper();
        var outMsg = objectMapper
                .writeValueAsString(req);

        if (config.endpoint == null || config.endpoint.isEmpty()) {
            throw new IllegalArgumentException("ShiroClient.reqres expected an endpoint to be set");
        }

        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.endpoint))
                .setHeader("Content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(outMsg));
        if (config.headers != null) {
            config.headers.forEach(requestBuilder::setHeader);
        }

        if (config.authToken != null && !config.authToken.isEmpty()) {
            requestBuilder.setHeader("Authorization", String.format("Bearer %s", config.authToken));
        }

        logger.debug(String.format("REQUEST:\n %s", outMsg));
        var response = this.httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.debug(String.format("RESPONSE:\n %s", response.body()));

        var typeFactory = objectMapper.getTypeFactory();
        MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, Object.class);
        HashMap<String, Object> map = objectMapper.readValue(response.body(), mapType);
        if (!map.containsKey("jsonrpc")) {
           throw new UnexpectedException("ShiroClient.reqres expected a jsonrpc field");
        }
        if (!(map.get("jsonrpc") instanceof String)) {
            throw new UnexpectedException("ShiroClient.reqres expected a string jsonrpc field");
        }
        if (!"2.0".equals(map.get("jsonrpc"))) {
            throw new UnexpectedException("ShiroClient.reqres expected jsonrpc version 2.0");
        }
        if (!map.containsKey("result")) {
            throw new UnexpectedException("ShiroClient.reqres expected a result field");
        }
        if (!(map.get("result") instanceof Map)) {
            throw new UnexpectedException("ShiroClient.reqres expected an object result field");
        }
        @SuppressWarnings (value="unchecked")
        Map<String, Object> resultArb = (Map<String, Object>) map.get("result");
        if (!resultArb.containsKey("error_level")) {
            throw new UnexpectedException("ShiroClient.reqres expected an error_level field");
        }
        if (!(resultArb.get("error_level") instanceof Number)) {
            throw new UnexpectedException("ShiroClient.reqres expected a numeric error_level field");
        }
        var errorLevel = ((Number) resultArb.get("error_level")).intValue();
        if (!resultArb.containsKey("result")) {
            throw new UnexpectedException("ShiroClient.reqres expected a result field");
        }
        var result = resultArb.get("result");
        if (!resultArb.containsKey("code")) {
            throw new UnexpectedException("ShiroClient.reqres expected a code field");
        }
        var code = resultArb.get("code");
        if (!resultArb.containsKey("message")) {
            throw new UnexpectedException("ShiroClient.reqres expected a message field");
        }
        var message = resultArb.get("message");
        if (!resultArb.containsKey("data")) {
            throw new UnexpectedException("ShiroClient.reqres expected a data field");
        }
        var data = resultArb.get("data");

        String txId = "";
        if (map.containsKey("$commit_tx_id")) {
            txId = (String) map.get("$commit_tx_id");
        }

        return new RpcRes(errorLevel, result, code, message, data, txId);
    }

    public interface ShiroResponse {
        <T> T unmarshal(Class<T> v) throws Exception;
        byte[] resultJson();
        String getTransactionId();
        Exception getError();
    }

    public static class ShiroSuccessResponse  implements ShiroResponse {
        private final String transactionId;
        private final byte[] result;

        ShiroSuccessResponse(String transactionId, byte[] result) {
            this.transactionId = transactionId;
            this.result = result;
        }

        @Override
        public <T> T unmarshal(Class<T> v) throws Exception {
            var mapper = new ObjectMapper();
            return mapper.readValue(result, v);
        }

        @Override
        public byte[] resultJson() {
            return result;
        }

        @Override
        public String getTransactionId() {
            return transactionId;
        }

        @Override
        public Exception getError() {
            return null;
        }
    }

    public static class ShiroFailureResponse implements ShiroResponse {

        private final int code;
        private final String message;
        private final byte[] data;

        ShiroFailureResponse(int code, String message, byte[] data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        @Override
        public <T> T unmarshal(Class<T> v) throws Exception {
            throw new UnexpectedException("can't unmarshal the result if the RPC call failed");
        }

        @Override
        public byte[] resultJson() {
            return new byte[0];
        }

        @Override
        public String getTransactionId() {
            return null;
        }

        @Override
        public Exception getError() {
            return new Exception(message);
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public byte[] getDataJson() {
            if (data == null) {
                return null;
            }
            return Arrays.copyOf(data,  data.length);
        }
    }

    public ShiroResponse call(String method, RequestOption ... opts) throws Exception {

        if (method == null || method.isEmpty()) {
           throw new IllegalArgumentException("method must be specified") ;
        }

        var config = applyOptions(opts);

        var transientJson = new HashMap<String, Object>();
        if (config.transientData != null) {
            config.transientData.forEach((key, value) -> transientJson.put(key, Hex.encodeHexString(value)));
        }

        if (config.timestampGenerator != null) {
            var ts = config.timestampGenerator.make();
            transientJson.put("timestamp_override", Hex.encodeHexString(ts.getBytes()));
        }

        var params = new HashMap<>() {{
            put("method", method);
            put("params", config.params);
            put("transient", transientJson);
        }};

        if (config.dependentTxId != null && !config.dependentTxId.isEmpty()) {
            params.put("dependent_txid", config.dependentTxId);
        }

        if (config.disableWritePolling) {
            params.put("disable_write_polling", true);
        }
        params.put("cc_fetchurl_downgrade", config.ccFetchUrlDowngrade);
        if (config.ccFetchUrlProxy != null && !config.ccFetchUrlProxy.isEmpty()) {
            params.put("cc_fetchurl_proxy", config.ccFetchUrlProxy);
        } else {
            params.put("cc_fetchurl_proxy", "");
        }

        var req = new HashMap<>() {{
            put("jsonrpc", "2.0");
            put("id", config.id);
            put("method", METHOD_CALL);
            put("params", params);
        }};

        if (config.mspFilter != null && config.mspFilter.length > 0) {
            params.put("msp_filter", config.mspFilter);
        }

        if (config.minEndorsers > 0) {
            params.put("min_endorsers", config.minEndorsers);
        }

        if (config.creator != null && !config.creator.isEmpty()) {
            params.put("creator_msp_id", config.creator);
        }

        var res = reqres(req, config);
        var objectMapper = new ObjectMapper();
        switch (res.errorLevel) {
            case ERROR_LEVEL_NO_ERROR:
                var resultJson = objectMapper.writeValueAsBytes(res.result);
                return new ShiroSuccessResponse(res.txId, resultJson);
            case ERROR_LEVEL_SHIROCLIENT:
                throw res.getShiroClientError();
            case ERROR_LEVEL_PHYLUM:
                var dataJson = objectMapper.writeValueAsBytes(res.data);

                if (!(res.code instanceof Number)) {
                    throw new UnexpectedException("ShiroClient.Call expected a numeric code field");
                }
                var code = ((Number) res.code).intValue();

                if (!(res.message instanceof String)) {
                    throw new UnexpectedException("ShiroClient.Call expected a string message field");
                }
                var message = (String) res.message;

                return new ShiroFailureResponse(code, message, dataJson);
            default:
                throw new UnexpectedException(String.format("ShiroClient.Call unexpected error level %d", res.errorLevel));
        }
    }
}
