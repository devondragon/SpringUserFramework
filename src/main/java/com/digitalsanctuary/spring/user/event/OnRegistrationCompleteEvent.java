package com.digitalsanctuary.spring.user.event;

import java.util.Locale;
import org.springframework.context.ApplicationEvent;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * The OnRegistrationCompleteEvent class is triggered when a user registers. We are using it to send the registration verification email, if enabled,
 * asynchronously. You can also listen for this event and perform any other post-registration processing desired.
 *
 * <p>
 * As of 5.0.0 this event no longer carries a live JPA {@code User} entity. Instead it exposes immutable scalar data
 * ({@code userId}, {@code userEmail}, {@code userEnabled}) captured at publish time, while the entity is still attached
 * to a persistence context. This prevents detached-entity / {@code LazyInitializationException} hazards when the event
 * is consumed by {@code @Async} listeners on a different thread. If a listener needs the full {@code User}, it should
 * load it by {@code userId} from {@code UserRepository} inside its own transaction.
 * </p>
 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class OnRegistrationCompleteEvent extends ApplicationEvent {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -7313587378037149313L;

	/** The app url. */
	private final String appUrl;

	/** The locale. */
	private final Locale locale;

	/** The id of the registered user. */
	private final Long userId;

	/** The email of the registered user (used as the verification-email recipient). */
	private final String userEmail;

	/**
	 * Whether the registered user is already enabled. First-time OAuth2/OIDC registrations are created enabled (the
	 * provider has already verified the email), so consumers such as the verification-email listener can skip sending
	 * a verification email to these users.
	 */
	private final boolean userEnabled;

	/**
	 * Instantiates a new on registration complete event.
	 *
	 * @param userId the id of the registered user
	 * @param userEmail the email of the registered user
	 * @param userEnabled whether the registered user is already enabled
	 * @param locale the locale
	 * @param appUrl the app url
	 */
	@Builder
	public OnRegistrationCompleteEvent(final Long userId, final String userEmail, final boolean userEnabled, final Locale locale,
			final String appUrl) {
		super(userId != null ? userId : "OnRegistrationCompleteEvent");
		this.userId = userId;
		this.userEmail = userEmail;
		this.userEnabled = userEnabled;
		this.locale = locale;
		this.appUrl = appUrl;
	}
}
