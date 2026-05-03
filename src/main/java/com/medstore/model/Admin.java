package com.medstore.model;

public final class Admin extends User {

    public Admin(Integer id, String email, String passwordHash, String fullName) {
        super(id, email, passwordHash, fullName);
    }

    @Override
    public UserRole getRole() {
        return UserRole.ADMIN;
    }

    @Override
    public boolean canManageCatalog() {
        return true;
    }

    @Override
    public boolean canDeleteMedicines() {
        return true;
    }

    @Override
    public boolean canDeleteUsers() {
        return true;
    }
}
