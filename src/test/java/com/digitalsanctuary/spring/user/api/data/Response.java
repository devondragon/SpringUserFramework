package com.digitalsanctuary.spring.user.api.data;


import java.util.Arrays;
import java.util.Objects;

public class Response {
    private boolean success;
    private String redirectUrl;
    private Integer code;
    private String[] messages;
    private Object data;

    public Response() {}

    public Response(boolean success, Integer code, String redirectUrl, String[] messages, Object data) {
        this.success = success;
        this.code = code;
        this.redirectUrl = redirectUrl;
        this.messages = messages;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String[] getMessages() {
        return messages;
    }

    public void setMessages(String[] messages) {
        this.messages = messages;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Response response)) return false;
        return response.success == this.success &&
                Objects.equals(response.redirectUrl, this.redirectUrl) &&
                response.code == this.code &&
                Arrays.equals(response.messages, this.messages) &&
                Objects.equals(response.data, this.data);
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "success=" + success +
                ", redirectUrl='" + redirectUrl + '\'' +
                ", code=" + code +
                ", messages=" + Arrays.toString(messages) +
                ", data=" + data +
                '}';
    }
}
