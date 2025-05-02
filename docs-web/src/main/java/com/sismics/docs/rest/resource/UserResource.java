package com.sismics.docs.rest.resource;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.sismics.docs.core.constant.AclTargetType;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.dao.*;
import com.sismics.docs.core.dao.criteria.GroupCriteria;
import com.sismics.docs.core.dao.criteria.UserCriteria;
import com.sismics.docs.core.dao.dto.GroupDto;
import com.sismics.docs.core.dao.dto.UserDto;
import com.sismics.docs.core.event.DocumentDeletedAsyncEvent;
import com.sismics.docs.core.event.FileDeletedAsyncEvent;
import com.sismics.docs.core.event.PasswordLostEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.*;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.RoutingUtil;
import com.sismics.docs.core.util.authentication.AuthenticationUtil;
import com.sismics.docs.core.util.jpa.SortCriteria;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.security.UserPrincipal;
import com.sismics.util.JsonUtil;
import com.sismics.util.context.ThreadLocalContext;
import com.sismics.util.filter.TokenBasedSecurityFilter;
import com.sismics.util.totp.GoogleAuthenticator;
import com.sismics.util.totp.GoogleAuthenticatorKey;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.http.Cookie;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * User REST resources.
 * 
 * @author jtremeaux
 */
@Path("/user")
public class UserResource extends BaseResource {
    /**
     * Creates a new user.
     * @param username User's username
     * @param password Password
     * @param email E-Mail
     * @return Response
     */
    @PUT
    public Response register(
        @FormParam("username") String username,
        @FormParam("password") String password,
        @FormParam("email") String email,
        @FormParam("storage_quota") String storageQuotaStr) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);
        
        // Validate the input data
        username = ValidationUtil.validateLength(username, "username", 3, 50);
        ValidationUtil.validateUsername(username, "username");
        password = ValidationUtil.validateLength(password, "password", 8, 50);
        email = ValidationUtil.validateLength(email, "email", 1, 100);
        Long storageQuota = ValidationUtil.validateLong(storageQuotaStr, "storage_quota");
        ValidationUtil.validateEmail(email, "email");
        
        // Create the user
        User user = new User();
        user.setRoleId(Constants.DEFAULT_USER_ROLE);
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setStorageQuota(storageQuota);
        user.setOnboarding(true);

        // Create the user
        UserDao userDao = new UserDao();
        try {
            userDao.create(user, principal.getId());
        } catch (Exception e) {
            if ("AlreadyExistingUsername".equals(e.getMessage())) {
                throw new ClientException("AlreadyExistingUsername", "Login already used", e);
            } else {
                throw new ServerException("UnknownError", "Unknown server error", e);
            }
        }
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Updates the current user informations.
     *
     *
     * @param password Password
     * @param email E-Mail
     * @return Response
     */
    @POST
    public Response update(
        @FormParam("password") String password,
        @FormParam("email") String email) {
        if (!authenticate() || principal.isGuest()) {
            throw new ForbiddenClientException();
        }
        
        // Validate the input data
        password = ValidationUtil.validateLength(password, "password", 8, 50, true);
        email = ValidationUtil.validateLength(email, "email", 1, 100, true);
        
        // Update the user
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(principal.getName());
        if (email != null) {
            user.setEmail(email);
        }
        user = userDao.update(user, principal.getId());
        
        // Change the password
        if (StringUtils.isNotBlank(password)) {
            user.setPassword(password);
            userDao.updatePassword(user, principal.getId());
        }
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Updates a user informations.
     *
     *
     * @param username Username
     * @param password Password
     * @param email E-Mail
     * @return Response
     */
    @POST
    @Path("{username: [a-zA-Z0-9_@.-]+}")
    public Response update(
        @PathParam("username") String username,
        @FormParam("password") String password,
        @FormParam("email") String email,
        @FormParam("storage_quota") String storageQuotaStr,
        @FormParam("disabled") Boolean disabled) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);
        
        // Validate the input data
        password = ValidationUtil.validateLength(password, "password", 8, 50, true);
        email = ValidationUtil.validateLength(email, "email", 1, 100, true);
        
        // Check if the user exists
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(username);
        if (user == null) {
            throw new ClientException("UserNotFound", "The user does not exist");
        }

        // Update the user
        if (email != null) {
            user.setEmail(email);
        }
        if (StringUtils.isNotBlank(storageQuotaStr)) {
            Long storageQuota = ValidationUtil.validateLong(storageQuotaStr, "storage_quota");
            user.setStorageQuota(storageQuota);
        }
        if (disabled != null) {
            // Cannot disable the admin user or the guest user
            RoleBaseFunctionDao userBaseFuction = new RoleBaseFunctionDao();
            Set<String> baseFunctionSet = userBaseFuction.findByRoleId(Sets.newHashSet(user.getRoleId()));
            if (Constants.GUEST_USER_ID.equals(username) || baseFunctionSet.contains(BaseFunction.ADMIN.name())) {
                disabled = false;
            }

            if (disabled && user.getDisableDate() == null) {
                // Recording the disabled date
                user.setDisableDate(new Date());
            } else if (!disabled && user.getDisableDate() != null) {
                // Emptying the disabled date
                user.setDisableDate(null);
            }
        }
        user = userDao.update(user, principal.getId());
        
        // Change the password
        if (StringUtils.isNotBlank(password)) {
            user.setPassword(password);
            userDao.updatePassword(user, principal.getId());
        }
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * This resource is used to authenticate the user and create a user session.
     * The "session" is only used to identify the user, no other data is stored in the session.
     *
     *
     * @param username Username
     * @param password Password
     * @param longLasted Remember the user next time, create a long lasted session.
     * @return Response
     */
    @POST
    @Path("login")
    public Response login(
        @FormParam("username") String username,
        @FormParam("password") String password,
        @FormParam("code") String validationCodeStr,
        @FormParam("remember") boolean longLasted) {
        // Validate the input data
        username = StringUtils.strip(username);
        password = StringUtils.strip(password);

        // Get the user
        UserDao userDao = new UserDao();
        User user = null;
        if (Constants.GUEST_USER_ID.equals(username)) {
            if (ConfigUtil.getConfigBooleanValue(ConfigType.GUEST_LOGIN)) {
                // Login as guest
                user = userDao.getActiveByUsername(Constants.GUEST_USER_ID);
            }
        } else {
            // Login as a normal user
            user = AuthenticationUtil.authenticate(username, password);
        }
        if (user == null) {
            throw new ForbiddenClientException();
        }

        // Two factor authentication
        if (user.getTotpKey() != null) {
            // If TOTP is enabled, ask a validation code
            if (Strings.isNullOrEmpty(validationCodeStr)) {
                throw new ClientException("ValidationCodeRequired", "An OTP validation code is required");
            }
            
            // Check the validation code
            int validationCode = ValidationUtil.validateInteger(validationCodeStr, "code");
            GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
            if (!googleAuthenticator.authorize(user.getTotpKey(), validationCode)) {
                throw new ForbiddenClientException();
            }
        }
        
        // Get the remote IP
        String ip = request.getHeader("x-forwarded-for");
        if (Strings.isNullOrEmpty(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // Create a new session token
        AuthenticationTokenDao authenticationTokenDao = new AuthenticationTokenDao();
        AuthenticationToken authenticationToken = new AuthenticationToken()
            .setUserId(user.getId())
            .setLongLasted(longLasted)
            .setIp(StringUtils.abbreviate(ip, 45))
            .setUserAgent(StringUtils.abbreviate(request.getHeader("user-agent"), 1000));
        String token = authenticationTokenDao.create(authenticationToken);
        
        // Cleanup old session tokens
        authenticationTokenDao.deleteOldSessionToken(user.getId());

        JsonObjectBuilder response = Json.createObjectBuilder();
        int maxAge = longLasted ? TokenBasedSecurityFilter.TOKEN_LONG_LIFETIME : -1;
        NewCookie cookie = new NewCookie(TokenBasedSecurityFilter.COOKIE_NAME, token, "/", null, null, maxAge, false);
        return Response.ok().entity(response.build()).cookie(cookie).build();
    }

    /**
     * Logs out the user and deletes the active session.
     *
     * @return Response
     */
    @POST
    @Path("logout")
    public Response logout() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the value of the session token
        String authToken = getAuthToken();
        
        AuthenticationTokenDao authenticationTokenDao = new AuthenticationTokenDao();
        AuthenticationToken authenticationToken = null;
        if (authToken != null) {
            authenticationToken = authenticationTokenDao.get(authToken);
        }
        
        // No token : nothing to do
        if (authenticationToken == null) {
            throw new ForbiddenClientException();
        }
        
        // Deletes the server token
        try {
            authenticationTokenDao.delete(authToken);
        } catch (Exception e) {
            throw new ServerException("AuthenticationTokenError", "Error deleting the authentication token: " + authToken, e);
        }
        
        // Deletes the client token in the HTTP response
        JsonObjectBuilder response = Json.createObjectBuilder();
        NewCookie cookie = new NewCookie(TokenBasedSecurityFilter.COOKIE_NAME, null, "/", null, 1, null, -1, new Date(1), false, false);
        return Response.ok().entity(response.build()).cookie(cookie).build();
    }

    /**
     * Deletes the current user.
     *
     * @return Response
     */
    @DELETE
    public Response delete() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Ensure that the admin or guest users are not deleted
        if (hasBaseFunction(BaseFunction.ADMIN) || principal.isGuest()) {
            throw new ClientException("ForbiddenError", "This user cannot be deleted");
        }

        // Check that this user is not used in any workflow
        String routeModelName = RoutingUtil.findRouteModelNameByTargetName(AclTargetType.USER, principal.getName());
        if (routeModelName != null) {
            throw new ClientException("UserUsedInRouteModel", routeModelName);
        }
        
        // Find linked data
        DocumentDao documentDao = new DocumentDao();
        List<Document> documentList = documentDao.findByUserId(principal.getId());
        FileDao fileDao = new FileDao();
        List<File> fileList = fileDao.findByUserId(principal.getId());
        
        // Delete the user
        UserDao userDao = new UserDao();
        userDao.delete(principal.getName(), principal.getId());
        
        sendDeletionEvents(documentList, fileList);

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Deletes a user.
     *
     * @param username Username
     * @return Response
     */
    @DELETE
    @Path("{username: [a-zA-Z0-9_@.-]+}")
    public Response delete(@PathParam("username") String username) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        // Cannot delete the guest user
        if (Constants.GUEST_USER_ID.equals(username)) {
            throw new ClientException("ForbiddenError", "The guest user cannot be deleted");
        }

        // Check that the user exists
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(username);
        if (user == null) {
            throw new ClientException("UserNotFound", "The user does not exist");
        }
        
        // Ensure that the admin user is not deleted
        RoleBaseFunctionDao roleBaseFunctionDao = new RoleBaseFunctionDao();
        Set<String> baseFunctionSet = roleBaseFunctionDao.findByRoleId(Sets.newHashSet(user.getRoleId()));
        if (baseFunctionSet.contains(BaseFunction.ADMIN.name())) {
            throw new ClientException("ForbiddenError", "The admin user cannot be deleted");
        }

        // Check that this user is not used in any workflow
        String routeModelName = RoutingUtil.findRouteModelNameByTargetName(AclTargetType.USER, username);
        if (routeModelName != null) {
            throw new ClientException("UserUsedInRouteModel", routeModelName);
        }
        
        // Find linked data
        DocumentDao documentDao = new DocumentDao();
        List<Document> documentList = documentDao.findByUserId(user.getId());
        FileDao fileDao = new FileDao();
        List<File> fileList = fileDao.findByUserId(user.getId());
        
        // Delete the user
        userDao.delete(user.getUsername(), principal.getId());

        sendDeletionEvents(documentList, fileList);

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Disable time-based one-time password for a specific user.
     * @param username Username
     * @return Response
     */
    @POST
    @Path("{username: [a-zA-Z0-9_@.-]+}/disable_totp")
    public Response disableTotpUsername(@PathParam("username") String username) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        // Get the user
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(username);
        if (user == null) {
            throw new ForbiddenClientException();
        }

        // Remove the TOTP key
        user.setTotpKey(null);
        userDao.update(user, principal.getId());

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Returns the information about the connected user.
     *
     *
     * @return Response
     */
    @GET
    public Response info() {
        JsonObjectBuilder response = Json.createObjectBuilder();
        if (!authenticate()) {
            response.add("anonymous", true);

            // Check if admin has the default password
            UserDao userDao = new UserDao();
            User adminUser = userDao.getById("admin");
            if (adminUser != null && adminUser.getDeleteDate() == null) {
                response.add("is_default_password", Constants.DEFAULT_ADMIN_PASSWORD.equals(adminUser.getPassword()));
            }
        } else {
            // Update the last connection date
            String authToken = getAuthToken();
            AuthenticationTokenDao authenticationTokenDao = new AuthenticationTokenDao();
            authenticationTokenDao.updateLastConnectionDate(authToken);
            
            // Build the response
            response.add("anonymous", false);
            UserDao userDao = new UserDao();
            GroupDao groupDao = new GroupDao();
            User user = userDao.getById(principal.getId());
            List<GroupDto> groupDtoList = groupDao.findByCriteria(new GroupCriteria()
                    .setUserId(user.getId())
                    .setRecursive(true), null);
            
            response.add("username", user.getUsername())
                    .add("email", user.getEmail())
                    .add("storage_quota", user.getStorageQuota())
                    .add("storage_current", user.getStorageCurrent())
                    .add("totp_enabled", user.getTotpKey() != null)
                    .add("onboarding", user.isOnboarding());

            // Base functions
            JsonArrayBuilder baseFunctions = Json.createArrayBuilder();
            for (String baseFunction : ((UserPrincipal) principal).getBaseFunctionSet()) {
                baseFunctions.add(baseFunction);
            }
            
            // Groups
            JsonArrayBuilder groups = Json.createArrayBuilder();
            for (GroupDto groupDto : groupDtoList) {
                groups.add(groupDto.getName());
            }
            
            response.add("base_functions", baseFunctions)
                    .add("groups", groups)
                    .add("is_default_password", hasBaseFunction(BaseFunction.ADMIN) && Constants.DEFAULT_ADMIN_PASSWORD.equals(user.getPassword()));
        }
        
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Returns the information about a user.
     *
     *
     * @param username Username
     * @return Response
     */
    @GET
    @Path("{username: [a-zA-Z0-9_@.-]+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response view(@PathParam("username") String username) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(username);
        if (user == null) {
            throw new ClientException("UserNotFound", "The user does not exist");
        }
        
        // Groups
        GroupDao groupDao = new GroupDao();
        List<GroupDto> groupDtoList = groupDao.findByCriteria(
                new GroupCriteria().setUserId(user.getId()),
                new SortCriteria(1, true));
        JsonArrayBuilder groups = Json.createArrayBuilder();
        for (GroupDto groupDto : groupDtoList) {
            groups.add(groupDto.getName());
        }
        
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("username", user.getUsername())
                .add("groups", groups)
                .add("email", user.getEmail())
                .add("totp_enabled", user.getTotpKey() != null)
                .add("storage_quota", user.getStorageQuota())
                .add("storage_current", user.getStorageCurrent())
                .add("disabled", user.getDisableDate() != null);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Returns all active users.
     *
     *
     * @param sortColumn Sort index
     * @param asc If true, ascending sorting, else descending
     * @param groupName Only return users from this group
     * @return Response
     */
    @GET
    @Path("list")
    public Response list(
            @QueryParam("sort_column") Integer sortColumn,
            @QueryParam("asc") Boolean asc,
            @QueryParam("group") String groupName) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        JsonArrayBuilder users = Json.createArrayBuilder();
        SortCriteria sortCriteria = new SortCriteria(sortColumn, asc);

        // Validate the group
        String groupId = null;
        if (!Strings.isNullOrEmpty(groupName)) {
            GroupDao groupDao = new GroupDao();
            Group group = groupDao.getActiveByName(groupName);
            if (group != null) {
                groupId = group.getId();
            }
        }
        
        UserDao userDao = new UserDao();
        List<UserDto> userDtoList = userDao.findByCriteria(new UserCriteria().setGroupId(groupId), sortCriteria);
        for (UserDto userDto : userDtoList) {
            users.add(Json.createObjectBuilder()
                    .add("id", userDto.getId())
                    .add("username", userDto.getUsername())
                    .add("email", userDto.getEmail())
                    .add("totp_enabled", userDto.getTotpKey() != null)
                    .add("storage_quota", userDto.getStorageQuota())
                    .add("storage_current", userDto.getStorageCurrent())
                    .add("create_date", userDto.getCreateTimestamp())
                    .add("disabled", userDto.getDisableTimestamp() != null));
        }
        
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("users", users);
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Returns all active sessions.
     *
     *
     * @return Response
     */
    @GET
    @Path("session")
    public Response session() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the value of the session token
        String authToken = getAuthToken();
        
        JsonArrayBuilder sessions = Json.createArrayBuilder();

        // The guest user cannot see other sessions
        if (!principal.isGuest()) {
            AuthenticationTokenDao authenticationTokenDao = new AuthenticationTokenDao();
            for (AuthenticationToken authenticationToken : authenticationTokenDao.getByUserId(principal.getId())) {
                JsonObjectBuilder session = Json.createObjectBuilder()
                        .add("create_date", authenticationToken.getCreationDate().getTime())
                        .add("ip", JsonUtil.nullable(authenticationToken.getIp()))
                        .add("user_agent", JsonUtil.nullable(authenticationToken.getUserAgent()));
                if (authenticationToken.getLastConnectionDate() != null) {
                    session.add("last_connection_date", authenticationToken.getLastConnectionDate().getTime());
                }
                session.add("current", authenticationToken.getId().equals(authToken));
                sessions.add(session);
            }
        }
        
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("sessions", sessions);
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Deletes all active sessions except the one used for this request.
     *
     *
     * @return Response
     */
    @DELETE
    @Path("session")
    public Response deleteSession() {
        if (!authenticate() || principal.isGuest()) {
            throw new ForbiddenClientException();
        }

        // Get the value of the session token
        String authToken = getAuthToken();
        
        // Remove other tokens
        AuthenticationTokenDao authenticationTokenDao = new AuthenticationTokenDao();
        authenticationTokenDao.deleteByUserId(principal.getId(), authToken);
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Mark the onboarding experience as passed.
     *
     *
     * @return Response
     */
    @POST
    @Path("onboarded")
    public Response onboarded() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Save it
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(principal.getName());
        user.setOnboarding(false);
        userDao.updateOnboarding(user);

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Enable time-based one-time password.
     *
     *
     * @return Response
     */
    @POST
    @Path("enable_totp")
    public Response enableTotp() {
        if (!authenticate() || principal.isGuest()) {
            throw new ForbiddenClientException();
        }
        
        // Create a new TOTP key
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        final GoogleAuthenticatorKey key = gAuth.createCredentials();
        
        // Save it
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(principal.getName());
        user.setTotpKey(key.getKey());
        userDao.update(user, principal.getId());
        
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("secret", key.getKey());
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Test time-based one-time password.
     *
     *
     * @return Response
     */
    @POST
    @Path("test_totp")
    public Response testTotp(@FormParam("code") String validationCodeStr) {
        if (!authenticate() || principal.isGuest()) {
            throw new ForbiddenClientException();
        }

        // Get the user
        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(principal.getName());

        // Test the validation code
        if (user.getTotpKey() != null) {
            int validationCode = ValidationUtil.validateInteger(validationCodeStr, "code");
            GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
            if (!googleAuthenticator.authorize(user.getTotpKey(), validationCode)) {
                throw new ForbiddenClientException();
            }
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
    
    /**
     * Disable time-based one-time password for the current user.
     *
     * @param password Password
     * @return Response
     */
    @POST
    @Path("disable_totp")
    public Response disableTotp(@FormParam("password") String password) {
        if (!authenticate() || principal.isGuest()) {
            throw new ForbiddenClientException();
        }
        
        // Validate the input data
        password = ValidationUtil.validateLength(password, "password", 1, 100, false);

        // Check the password and get the user
        UserDao userDao = new UserDao();
        User user = userDao.authenticate(principal.getName(), password);
        if (user == null) {
            throw new ForbiddenClientException();
        }
        
        // Remove the TOTP key
        user.setTotpKey(null);
        userDao.update(user, principal.getId());
        
        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Create a key to reset a password and send it by email.
     *
     * @param username Username
     * @return Response
     */
    @POST
    @Path("password_lost")
    @Produces(MediaType.APPLICATION_JSON)
    public Response passwordLost(@FormParam("username") String username) {
        authenticate();

        // Validate input data
        ValidationUtil.validateStringNotBlank("username", username);

        // Prepare response
        Response response = Response.ok().entity(Json.createObjectBuilder()
                .add("status", "ok")
                .build()).build();

        // Check for user existence
        UserDao userDao = new UserDao();
        List<UserDto> userDtoList = userDao.findByCriteria(new UserCriteria().setUserName(username), null);
        if (userDtoList.isEmpty()) {
            return response;
        }
        UserDto user = userDtoList.get(0);

        // Create the password recovery key
        PasswordRecoveryDao passwordRecoveryDao = new PasswordRecoveryDao();
        PasswordRecovery passwordRecovery = new PasswordRecovery();
        passwordRecovery.setUsername(user.getUsername());
        passwordRecoveryDao.create(passwordRecovery);

        // Fire a password lost event
        PasswordLostEvent passwordLostEvent = new PasswordLostEvent();
        passwordLostEvent.setUser(user);
        passwordLostEvent.setPasswordRecovery(passwordRecovery);
        AppContext.getInstance().getMailEventBus().post(passwordLostEvent);

        // Always return OK
        return response;
    }

    /**
     * Reset the user's password.
     *
     * @param passwordResetKey Password reset key
     * @param password New password
     * @return Response
     */
    @POST
    @Path("password_reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response passwordReset(
            @FormParam("key") String passwordResetKey,
            @FormParam("password") String password) {
        authenticate();

        // Validate input data
        ValidationUtil.validateRequired("key", passwordResetKey);
        password = ValidationUtil.validateLength(password, "password", 8, 50, true);

        // Load the password recovery key
        PasswordRecoveryDao passwordRecoveryDao = new PasswordRecoveryDao();
        PasswordRecovery passwordRecovery = passwordRecoveryDao.getActiveById(passwordResetKey);
        if (passwordRecovery == null) {
            throw new ClientException("KeyNotFound", "Password recovery key not found");
        }

        UserDao userDao = new UserDao();
        User user = userDao.getActiveByUsername(passwordRecovery.getUsername());

        // Change the password
        user.setPassword(password);
        user = userDao.updatePassword(user, principal.getId());

        // Deletes password recovery requests
        passwordRecoveryDao.deleteActiveByLogin(user.getUsername());

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Returns the authentication token value.
     *
     * @return Token value
     */
    private String getAuthToken() {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (TokenBasedSecurityFilter.COOKIE_NAME.equals(cookie.getName())
                        && !Strings.isNullOrEmpty(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Send the events about documents and files being deleted.
     * @param documentList A document list
     * @param fileList A file list
     */
    private void sendDeletionEvents(List<Document> documentList, List<File> fileList) {
        // Raise deleted events for documents
        for (Document document : documentList) {
            DocumentDeletedAsyncEvent documentDeletedAsyncEvent = new DocumentDeletedAsyncEvent();
            documentDeletedAsyncEvent.setUserId(principal.getId());
            documentDeletedAsyncEvent.setDocumentId(document.getId());
            ThreadLocalContext.get().addAsyncEvent(documentDeletedAsyncEvent);
        }

        // Raise deleted events for files (don't bother sending document updated event)
        for (File file : fileList) {
            FileDeletedAsyncEvent fileDeletedAsyncEvent = new FileDeletedAsyncEvent();
            fileDeletedAsyncEvent.setUserId(principal.getId());
            fileDeletedAsyncEvent.setFileId(file.getId());
            fileDeletedAsyncEvent.setFileSize(file.getSize());
            ThreadLocalContext.get().addAsyncEvent(fileDeletedAsyncEvent);
        }
    }

}
