package com.sismics.docs.core.service;

import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.constant.UserRegistrationRequestStatus;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.UserRegistrationRequestDao;
import com.sismics.docs.core.dao.dto.UserDto;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.model.jpa.UserRegistrationRequest;
import com.sismics.docs.core.util.TransactionUtil;
import com.sismics.util.EmailUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User registration request service.
 *
 * @author bgamard
 */
public class UserRegistrationRequestService {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(UserRegistrationRequestService.class);

    /**
     * Creates a new user registration request.
     *
     * @param username Username
     * @param email Email
     * @return Request ID
     */
    public String createRequest(String username, String email) {
        final String[] requestId = new String[1];
        TransactionUtil.handle(() -> {
            // Check if username already exists
            UserDao userDao = new UserDao();
            if (userDao.getActiveByUsername(username) != null) {
                throw new IllegalArgumentException("Username already exists");
            }

            // Check if email already exists
            if (userDao.getActiveByEmail(email) != null) {
                throw new IllegalArgumentException("Email already exists");
            }

            // Create the request
            UserRegistrationRequest request = new UserRegistrationRequest();
            request.setUsername(username);
            request.setEmail(email);

            UserRegistrationRequestDao requestDao = new UserRegistrationRequestDao();
            requestId[0] = requestDao.create(request);
        });
        return requestId[0];
    }

    /**
     * Returns the list of all pending user registration requests.
     *
     * @return List of user registration requests
     */
    public List<UserRegistrationRequest> findAllPending() {
        final List<UserRegistrationRequest>[] requests = new List[1];
        TransactionUtil.handle(() -> {
            UserRegistrationRequestDao requestDao = new UserRegistrationRequestDao();
            requests[0] = requestDao.findAllPending();
        });
        return requests[0];
    }

    /**
     * Approves a user registration request.
     *
     * @param requestId Request ID
     * @param adminId Admin user ID
     */
    public void approveRequest(String requestId, String adminId) {
        TransactionUtil.handle(() -> {
            UserRegistrationRequestDao requestDao = new UserRegistrationRequestDao();
            UserRegistrationRequest request = requestDao.getActiveById(requestId);
            if (request == null) {
                throw new IllegalArgumentException("Request not found");
            }

            // Update request status
            request.setStatus(UserRegistrationRequestStatus.APPROVED);
            requestDao.update(request, adminId);

            // Create user account
            UserDao userDao = new UserDao();
            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setPassword(request.getUsername());
            user.setRoleId(Constants.DEFAULT_USER_ROLE);
            user.setStorageQuota(1000000L);
            try {
                userDao.create(user, adminId);
            } catch (Exception e) {
                log.error("Error creating user account", e);
                throw new RuntimeException("Error creating user account", e);
            }

            // Send email notification
            try {
                UserDto userDto = new UserDto();
                userDto.setUsername(request.getUsername());
                userDto.setEmail(request.getEmail());

                Map<String, Object> rootMap = new HashMap<>();
                rootMap.put("username", request.getUsername());
                rootMap.put("email", request.getEmail());

                EmailUtil.sendEmail("user_registration_approved", userDto, rootMap);
            } catch (Exception e) {
                log.error("Error sending approval email", e);
            }
        });
    }

    /**
     * Rejects a user registration request.
     *
     * @param requestId Request ID
     * @param adminId Admin user ID
     * @param reason Rejection reason
     */
    public void rejectRequest(String requestId, String adminId, String reason) {
        TransactionUtil.handle(() -> {
            UserRegistrationRequestDao requestDao = new UserRegistrationRequestDao();
            UserRegistrationRequest request = requestDao.getActiveById(requestId);
            if (request == null) {
                throw new IllegalArgumentException("Request not found");
            }

            // Update request status
            request.setStatus(UserRegistrationRequestStatus.REJECTED);
            request.setReason(reason);
            requestDao.update(request, adminId);

            // Send email notification
            try {
                UserDto userDto = new UserDto();
                userDto.setUsername(request.getUsername());
                userDto.setEmail(request.getEmail());

                Map<String, Object> rootMap = new HashMap<>();
                rootMap.put("username", request.getUsername());
                rootMap.put("email", request.getEmail());
                rootMap.put("reason", reason);

                EmailUtil.sendEmail("user_registration_rejected", userDto, rootMap);
            } catch (Exception e) {
                log.error("Error sending rejection email", e);
            }
        });
    }
}