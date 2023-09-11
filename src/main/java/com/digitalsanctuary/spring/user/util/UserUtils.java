package com.digitalsanctuary.spring.user.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility class for user-related operations.
 */
public final class UserUtils {

	/**
	 * Private constructor to prevent instantiation.
	 */
	private UserUtils() {
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Get the client's IP address.
	 *
	 * @param request The HttpServletRequest object.
	 * @return The client's IP address as a String.
	 */
	public static String getClientIP(HttpServletRequest request) {
		String xfHeader = request.getHeader("X-Forwarded-For");
		if (xfHeader != null) {
			return xfHeader.split(",")[0];
		}
		return request.getRemoteAddr();
	}

	/**
	 * Get the application URL based on the provided request.
	 *
	 * @param request The HttpServletRequest object.
	 * @return The application URL as a String.
	 */
	public static String getAppUrl(HttpServletRequest request) {
		return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
	}
}
