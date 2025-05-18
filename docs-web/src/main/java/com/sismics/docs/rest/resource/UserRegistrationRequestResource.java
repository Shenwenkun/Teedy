package com.sismics.docs.rest.resource;

import com.sismics.docs.core.model.jpa.UserRegistrationRequest;
import com.sismics.docs.core.service.UserRegistrationRequestService;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.util.ValidationUtil;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * User registration request REST resources.
 *
 * @author bgamard
 */
@Path("/registration")
public class UserRegistrationRequestResource extends BaseResource {
    /**
     * Create a new user registration request.
     *
     * @api {put} /registration Create a new user registration request
     * @apiName PutRegistration
     * @apiGroup Registration
     * @apiParam {String} username Username
     * @apiParam {String} email Email
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) AlreadyExistError Username or email already exists
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param username Username
     * @param email Email
     * @return Response
     */
    @PUT
    public Response register(
            @FormParam("username") String username,
            @FormParam("email") String email) {
        // Validate input data
        username = ValidationUtil.validateLength(username, "username", 3, 50, false);
        email = ValidationUtil.validateLength(email, "email", 3, 50, false);
        ValidationUtil.validateEmail(email, "email");

        // Create the request
        UserRegistrationRequestService service = new UserRegistrationRequestService();
        try {
            service.createRequest(username, email);
        } catch (Exception e) {
            throw new ClientException("AlreadyExistError", e.getMessage());
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Returns the list of all pending registration requests.
     *
     * @api {get} /registration Get registration requests
     * @apiName GetRegistration
     * @apiGroup Registration
     * @apiSuccess {Object[]} requests List of registration requests
     * @apiSuccess {String} requests.id ID
     * @apiSuccess {String} requests.username Username
     * @apiSuccess {String} requests.email Email
     * @apiSuccess {String} requests.status Status
     * @apiSuccess {String} requests.reason Rejection reason
     * @apiSuccess {Number} requests.create_date Create date (timestamp)
     * @apiSuccess {Number} requests.update_date Update date (timestamp)
     * @apiError (client) ForbiddenError Access denied
     * @apiPermission admin
     * @apiVersion 1.5.0
     *
     * @return Response
     */
    @GET
    public Response list() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        JsonArrayBuilder requests = Json.createArrayBuilder();
        UserRegistrationRequestService service = new UserRegistrationRequestService();
        List<UserRegistrationRequest> requestList = service.findAllPending();
        for (UserRegistrationRequest request : requestList) {
            JsonObjectBuilder requestJson = Json.createObjectBuilder()
                    .add("id", request.getId())
                    .add("username", request.getUsername())
                    .add("email", request.getEmail())
                    .add("status", request.getStatus().name())
                    .add("create_date", request.getCreateDate().getTime());
            if (request.getUpdateDate() != null) {
                requestJson.add("update_date", request.getUpdateDate().getTime());
            }
            if (request.getReason() != null) {
                requestJson.add("reason", request.getReason());
            }
            requests.add(requestJson);
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("requests", requests);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Approve a registration request.
     *
     * @api {post} /registration/:id/approve Approve a registration request
     * @apiName PostRegistrationApprove
     * @apiGroup Registration
     * @apiParam {String} id Registration request ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Registration request not found
     * @apiPermission admin
     * @apiVersion 1.5.0
     *
     * @param id Registration request ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/approve")
    public Response approve(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        // Approve the request
        UserRegistrationRequestService service = new UserRegistrationRequestService();
        try {
            service.approveRequest(id, principal.getId());
        } catch (Exception e) {
            throw new NotFoundException();
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Reject a registration request.
     *
     * @api {post} /registration/:id/reject Reject a registration request
     * @apiName PostRegistrationReject
     * @apiGroup Registration
     * @apiParam {String} id Registration request ID
     * @apiParam {String} reason Rejection reason
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Registration request not found
     * @apiError (client) ValidationError Validation error
     * @apiPermission admin
     * @apiVersion 1.5.0
     *
     * @param id Registration request ID
     * @param reason Rejection reason
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/reject")
    public Response reject(
            @PathParam("id") String id,
            @FormParam("reason") String reason) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        // Validate input data
        reason = ValidationUtil.validateLength(reason, "reason", 1, 500, false);

        // Reject the request
        UserRegistrationRequestService service = new UserRegistrationRequestService();
        try {
            service.rejectRequest(id, principal.getId(), reason);
        } catch (Exception e) {
            throw new NotFoundException();
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }
}