package com.medstore.servlet;

import com.medstore.dao.UserDAO;
import com.medstore.model.UserRole;
import com.medstore.util.PasswordHasher;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Seeds a default administrator if the database has no rows yet (development convenience).
 */
@WebListener
public class AppBootstrapListener implements ServletContextListener {

    static final String SEED_EMAIL = "admin@medstore.local";
    static final String SEED_PASSWORD = "Admin123!";
    static final String SEED_NAME = "Portal Admin";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            UserDAO dao = new UserDAO();
            if (dao.countUsers() == 0) {
                dao.insertRegistered(SEED_EMAIL, PasswordHasher.hash(SEED_PASSWORD), SEED_NAME, UserRole.ADMIN);
                sce.getServletContext().log("Bootstrap: seeded admin '" + SEED_EMAIL + "'");
            }
        } catch (Exception e) {
            sce.getServletContext().log("Bootstrap skipped (check MySQL schema & db.properties): " + e.getMessage(), e);
        }
    }
}
