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
	 * Get the client's IP address by checking various headers commonly used by proxies, load balancers, and CDNs.
	 * 
	 * Checks headers in order of preference:
	 * 1. X-Forwarded-For (standard proxy header)
	 * 2. X-Real-IP (nginx and other reverse proxies)
	 * 3. CF-Connecting-IP (Cloudflare)
	 * 4. True-Client-IP (Akamai, Cloudflare Enterprise)
	 * 5. Falls back to request.getRemoteAddr()
	 *
	 * @param request The HttpServletRequest object.
	 * @return The client's IP address as a String.
	 */
	public static String getClientIP(HttpServletRequest request) {
		// Array of header names to check in order of preference
		String[] ipHeaders = {
			"X-Forwarded-For",
			"X-Real-IP", 
			"CF-Connecting-IP",
			"True-Client-IP"
		};
		
		for (String header : ipHeaders) {
			String ip = request.getHeader(header);
			if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
				// X-Forwarded-For can contain multiple IPs, take the first one
				if ("X-Forwarded-For".equals(header)) {
					return ip.split(",")[0].trim();
				}
				return ip.trim();
			}
		}
		
		// Fall back to remote address if no proxy headers found
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
