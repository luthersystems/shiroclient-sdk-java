package com.luthersystems.shiroclient;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class ShiroResponse {
    private String transactionId;
    private byte[] result;

    public <T> T unmarshal(Class<T> v) throws IOException, JsonParseException, JsonMappingException {
        var mapper = new ObjectMapper();
        var obj =  mapper.readValue(result, v);
        return obj;
    }

    public byte[] resultJson() {
        return result;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
