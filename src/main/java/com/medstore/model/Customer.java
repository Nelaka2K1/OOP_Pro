package com.medstore.model;

public final class Customer extends User {

    public Customer(Integer id, String email, String passwordHash, String fullName, String profileImagePath) {
        super(id, email, passwordHash, fullName, profileImagePath);
    }

    @Override
    public UserRole getRole() {
        return UserRole.CUSTOMER;
    }

    @Override
    public boolean canManageCatalog() {
        return false;
    }

    @Override
    public boolean canDeleteMedicines() {
        return false;
    }

    @Override
    public boolean canDeleteUsers() {
        return false;
    }
}
