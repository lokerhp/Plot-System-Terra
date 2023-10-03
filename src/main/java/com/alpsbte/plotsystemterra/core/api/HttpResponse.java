package com.alpsbte.plotsystemterra.core.api;

import com.google.gson.JsonElement;

public class HttpResponse {

    private final int responseCode;
    private final JsonElement responseBody;

    public HttpResponse(JsonElement responseBody, int responseCode) {
        this.responseBody = responseBody;
        this.responseCode = responseCode;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public JsonElement getBody() {
        return responseBody;
    }
}
