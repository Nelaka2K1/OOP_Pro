package com.medstore.model;

/**
 * Abstract user account — subclasses supply role-specific authorization (polymorphism).
 */
public abstract class User {
    private final Integer id;
    private final String email;
    private final String passwordHash;
    private String fullName;
    private String profileImagePath;

    protected User(Integer id, String email, String passwordHash, String fullName, String profileImagePath) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.profileImagePath = profileImagePath;
    }

    /** Role for persistence and RBAC checks. */
    public abstract UserRole getRole();

    /** Whether this user may add catalogue items on the website. */
    public abstract boolean canManageCatalog();

    /** Whether this user may remove catalogue items. */
    public abstract boolean canDeleteMedicines();

    /** Whether this user may remove other accounts. */
    public abstract boolean canDeleteUsers();

    /** Whether this user may edit or delete receipts. */
    public boolean canManageReceipts() {
        return false;
    }

    public Integer getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getProfileImagePath() {
        return profileImagePath;
    }

    public void setProfileImagePath(String profileImagePath) {
        this.profileImagePath = profileImagePath;
    }

    /** Safe view for JSON / UI (no password hash). */
    public UserPublicView toPublicView() {
        return new UserPublicView(getId(), getEmail(), getFullName(), getRole().name(), getProfileImagePath());
    }

    /** Factory: build the correct subtype from persisted role string. */
    public static User hydrate(Integer id, String email, String passwordHash, String fullName,
                               UserRole role, String profileImagePath) {
        return switch (role) {
            case CUSTOMER -> new Customer(id, email, passwordHash, fullName, profileImagePath);
            case PHARMACIST -> new Pharmacist(id, email, passwordHash, fullName, profileImagePath);
            case ADMIN -> new Admin(id, email, passwordHash, fullName, profileImagePath);
            case ACCOUNTANT -> new Accountant(id, email, passwordHash, fullName, profileImagePath);
        };
    }
}
