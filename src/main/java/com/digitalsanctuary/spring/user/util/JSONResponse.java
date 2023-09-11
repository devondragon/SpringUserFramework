package com.digitalsanctuary.spring.user.util;

import lombok.Data;

@Data
public class JSONResponse {

	private boolean success;
	private String redirectUrl;
	private Integer code;
	private String[] messages;

	public JSONResponse(boolean pSuccess, String pRedirectUrl, Integer pCode, String... pMessages) {
		super();
		success = pSuccess;
		redirectUrl = pRedirectUrl;
		messages = pMessages;
		code = pCode;
	}

	public JSONResponse(boolean pSuccess, String pRedirectUrl, String... pMessages) {
		super();
		success = pSuccess;
		redirectUrl = pRedirectUrl;
		messages = pMessages;
		code = null;
	}

	public JSONResponse(boolean pSuccess, String... pMessages) {
		super();
		success = pSuccess;
		redirectUrl = null;
		messages = pMessages;
		code = null;
	}

}
