package com.digitalsanctuary.spring.user.util;

import jakarta.servlet.http.HttpServletRequest;

public abstract class UserUtils {
	public static String getClientIP(HttpServletRequest request) {
		String ip = null;
		final String xfHeader = request.getHeader("X-Forwarded-For");
		if (xfHeader != null) {
			ip = xfHeader.split(",")[0];
		} else {
			ip = request.getRemoteAddr();
		}

		return ip;
	}

	public static String getAppUrl(HttpServletRequest request) {
		return "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
	}
}
