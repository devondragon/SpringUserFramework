package com.digitalsanctuary.spring.user.event;

import java.util.Locale;
import org.springframework.context.ApplicationEvent;
import org.springframework.scheduling.annotation.Async;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The OnRegistrationCompleteEvent class is triggered when a user registers. We are using to send the registration verification email, if enabled,
 * asynchronously. You can also listen for this event and perform any other post-registration processing desired.
 */
@Async
@Data
@EqualsAndHashCode(callSuper = false)
public class OnRegistrationCompleteEvent extends ApplicationEvent {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -7313587378037149313L;

	/** The app url. */
	private final String appUrl;

	/** The locale. */
	private final Locale locale;

	/** The user. */
	private final User user;

	/**
	 * Instantiates a new on registration complete event.
	 *
	 * @param user the user
	 * @param locale the locale
	 * @param appUrl the app url
	 */
	@Builder
	public OnRegistrationCompleteEvent(final User user, final Locale locale, final String appUrl) {
		super(user);
		this.user = user;
		this.locale = locale;
		this.appUrl = appUrl;
	}
}
