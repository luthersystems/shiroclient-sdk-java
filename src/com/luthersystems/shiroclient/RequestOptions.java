package com.luthersystems.shiroclient;

import java.util.Map;

class RequestOptions {
    private Map<String, String> headers;
    private String endpoint;
    String id;
    private String authToken;
    private Object params;
    Map<String, byte[]> transientData;
    // target: leave out target/response
    private String[] mspFilter;
    private int minEndorsers;
    private String creator;
    // ctx: do not support async/ctx
    private String dependentTxId;
    private boolean disableWritePolling;
    private boolean ccFetchUrlDowngrade;
    private String ccFetchUrlProxy;
    TimestampGenerator timestampGenerator;

    public interface RequestOption {
        void apply(RequestOptions opts);
    }

    public interface TimestampGenerator {
        String make();
    }

    public static RequestOption WithId(String id) {
        return opts -> opts.id = id;
    }
}

