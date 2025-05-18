package com.sismics.docs.core.dao;

import com.sismics.docs.core.constant.AuditLogType;
import com.sismics.docs.core.constant.UserRegistrationRequestStatus;
import com.sismics.docs.core.model.jpa.UserRegistrationRequest;
import com.sismics.docs.core.util.AuditLogUtil;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * User registration request DAO.
 *
 * @author bgamard
 */
public class UserRegistrationRequestDao {
    /**
     * Creates a new user registration request.
     *
     * @param request User registration request
     * @return New ID
     */
    public String create(UserRegistrationRequest request) {
        // Create the UUID
        request.setId(UUID.randomUUID().toString());

        // Create the request
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        request.setCreateDate(new Date());
        request.setStatus(UserRegistrationRequestStatus.PENDING);
        em.persist(request);

        return request.getId();
    }

    /**
     * Returns a user registration request by ID.
     *
     * @param id Request ID
     * @return User registration request
     */
    public UserRegistrationRequest getActiveById(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            Query q = em.createQuery("select r from UserRegistrationRequest r where r.id = :id and r.deleteDate is null");
            q.setParameter("id", id);
            return (UserRegistrationRequest) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Returns the list of all pending user registration requests.
     *
     * @return List of user registration requests
     */
    @SuppressWarnings("unchecked")
    public List<UserRegistrationRequest> findAllPending() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query q = em.createQuery("select r from UserRegistrationRequest r where r.status = :status and r.deleteDate is null order by r.createDate desc");
        q.setParameter("status", UserRegistrationRequestStatus.PENDING);
        return q.getResultList();
    }

    /**
     * Updates a user registration request.
     *
     * @param request User registration request
     * @param userId User ID
     * @return Updated request
     */
    public UserRegistrationRequest update(UserRegistrationRequest request, String userId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();

        // Get the request
        Query q = em.createQuery("select r from UserRegistrationRequest r where r.id = :id and r.deleteDate is null");
        q.setParameter("id", request.getId());
        UserRegistrationRequest requestDb = (UserRegistrationRequest) q.getSingleResult();

        // Update the request
        requestDb.setStatus(request.getStatus());
        requestDb.setReason(request.getReason());
        requestDb.setUpdateDate(new Date());

        // Create audit log
        AuditLogUtil.create(requestDb, AuditLogType.UPDATE, userId);

        return requestDb;
    }

    /**
     * Deletes a user registration request.
     *
     * @param id Request ID
     * @param userId User ID
     */
    public void delete(String id, String userId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();

        // Get the request
        Query q = em.createQuery("select r from UserRegistrationRequest r where r.id = :id and r.deleteDate is null");
        q.setParameter("id", id);
        UserRegistrationRequest requestDb = (UserRegistrationRequest) q.getSingleResult();

        // Delete the request
        Date dateNow = new Date();
        requestDb.setDeleteDate(dateNow);

        // Create audit log
        AuditLogUtil.create(requestDb, AuditLogType.DELETE, userId);
    }
}