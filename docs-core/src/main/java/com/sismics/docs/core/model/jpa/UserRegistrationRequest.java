package com.sismics.docs.core.model.jpa;

import com.google.common.base.MoreObjects;
import com.sismics.docs.core.constant.UserRegistrationRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Date;

/**
 * User registration request entity.
 *
 * @author bgamard
 */
@Entity
@Table(name = "T_USER_REGISTRATION_REQUEST")
public class UserRegistrationRequest implements Loggable {
    /**
     * Request ID.
     */
    @Id
    @Column(name = "URR_ID_C", length = 36)
    private String id;

    /**
     * Username.
     */
    @Column(name = "URR_USERNAME_C", nullable = false, length = 50)
    private String username;

    /**
     * Email.
     */
    @Column(name = "URR_EMAIL_C", nullable = false, length = 100)
    private String email;

    /**
     * Status.
     */
    @Column(name = "URR_STATUS_C", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private UserRegistrationRequestStatus status;

    /**
     * Reason for rejection.
     */
    @Column(name = "URR_REASON_C", length = 4000)
    private String reason;

    /**
     * Creation date.
     */
    @Column(name = "URR_CREATEDATE_D", nullable = false)
    private Date createDate;

    /**
     * Update date.
     */
    @Column(name = "URR_UPDATEDATE_D")
    private Date updateDate;

    /**
     * Deletion date.
     */
    @Column(name = "URR_DELETEDATE_D")
    private Date deleteDate;

    public String getId() {
        return id;
    }

    public UserRegistrationRequest setId(String id) {
        this.id = id;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public UserRegistrationRequest setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public UserRegistrationRequest setEmail(String email) {
        this.email = email;
        return this;
    }

    public UserRegistrationRequestStatus getStatus() {
        return status;
    }

    public UserRegistrationRequest setStatus(UserRegistrationRequestStatus status) {
        this.status = status;
        return this;
    }

    public String getReason() {
        return reason;
    }

    public UserRegistrationRequest setReason(String reason) {
        this.reason = reason;
        return this;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public UserRegistrationRequest setCreateDate(Date createDate) {
        this.createDate = createDate;
        return this;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public UserRegistrationRequest setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
        return this;
    }

    @Override
    public Date getDeleteDate() {
        return deleteDate;
    }

    public UserRegistrationRequest setDeleteDate(Date deleteDate) {
        this.deleteDate = deleteDate;
        return this;
    }

    @Override
    public String toMessage() {
        return username;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("username", username)
                .add("email", email)
                .add("status", status)
                .toString();
    }
}