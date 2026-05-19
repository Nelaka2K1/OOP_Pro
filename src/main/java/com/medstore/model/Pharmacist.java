package com.medstore.model;

public final class Pharmacist extends User {

    public Pharmacist(Integer id, String email, String passwordHash, String fullName, String profileImagePath) {
        super(id, email, passwordHash, fullName, profileImagePath);
    }

    @Override
    public UserRole getRole() {
        return UserRole.PHARMACIST;
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
        return false;
    }
}
