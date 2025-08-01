package org.apereo.cas.support.oauth;

import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.oauth.util.OAuth20Utils;
import org.apereo.cas.support.oauth.web.OAuth20RequestParameterResolver;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This is {@link OAuth20ClientIdAwareProfileManager}.
 * It saves returns a profile based on client_id from the request.
 *
 * @author Kirill Gagarski
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@Slf4j
public class OAuth20ClientIdAwareProfileManager extends ProfileManager {

    private static final String SESSION_CLIENT_ID = "oauthClientId";

    private final ServicesManager servicesManager;

    private final OAuth20RequestParameterResolver requestParameterResolver;

    public OAuth20ClientIdAwareProfileManager(final WebContext context,
                                              final SessionStore sessionStore,
                                              final ServicesManager servicesManager,
                                              final OAuth20RequestParameterResolver requestParameterResolver) {
        super(context, sessionStore);
        this.servicesManager = servicesManager;
        this.requestParameterResolver = requestParameterResolver;
    }

    @Override
    @SuppressWarnings("NonApiType")
    protected LinkedHashMap<String, UserProfile> retrieveAll(final boolean readFromSession) {
        val profiles = super.retrieveAll(readFromSession).entrySet();
        val clientId = getClientIdFromRequest();
        val results = profiles
            .stream()
            .filter(it -> {
                val profile = it.getValue();
                return StringUtils.isBlank(clientId)
                       || Strings.CI.equals((CharSequence) profile.getAttribute(SESSION_CLIENT_ID), clientId);
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> {
                    throw new IllegalStateException("Duplicate key");
                },
                LinkedHashMap::new));
        LOGGER.trace("Fetched profiles for this session are [{}]", results);
        return results;
    }

    @Override
    public void save(final boolean saveInSession, final UserProfile profile, final boolean multiProfile) {
        val clientId = getClientIdFromRequest();
        if (StringUtils.isNotBlank(clientId)) {
            profile.addAttribute(SESSION_CLIENT_ID, clientId);
        }
        super.save(saveInSession, profile, multiProfile);
    }

    private String getClientIdFromRequest() {
        var clientId = requestParameterResolver.resolveRequestParameter(context, OAuth20Constants.CLIENT_ID)
            .map(String::valueOf).orElse(StringUtils.EMPTY);
        if (StringUtils.isBlank(clientId)) {
            val redirectUri = requestParameterResolver.resolveRequestParameter(context, OAuth20Constants.REDIRECT_URI)
                .map(String::valueOf).orElse(StringUtils.EMPTY);
            OAuth20Utils.validateRedirectUri(redirectUri);
            val svc = OAuth20Utils.getRegisteredOAuthServiceByRedirectUri(this.servicesManager, redirectUri);
            clientId = svc != null ? svc.getClientId() : StringUtils.EMPTY;
        }
        return clientId;
    }
}
