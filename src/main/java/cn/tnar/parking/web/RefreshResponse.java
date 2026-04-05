package cn.tnar.parking.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefreshResponse {

    @JsonProperty("code")
    private final int code;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("data")
    private final Data data;

    private RefreshResponse(int code, String message, Data data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static RefreshResponse success(int refreshed) {
        return new RefreshResponse(1, "success", new Data(refreshed));
    }

    public static RefreshResponse error(String message) {
        return new RefreshResponse(2, message, null);
    }

    public static class Data {
        @JsonProperty("refreshed")
        private final int refreshed;

        public Data(int refreshed) {
            this.refreshed = refreshed;
        }
    }
}
