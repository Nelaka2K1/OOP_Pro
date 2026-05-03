# Medicine online store (Java + MySQL)

Full-stack coursework-style pharmacy storefront: Jakarta Servlet APIs on Java 17, MySQL for persistence, and a static HTML/CSS/JS dashboard with responsive “glass” visuals.

## OOP highlights

- **Encapsulation** — Entities (`Medicine`, `CartLine`, `UserPublicView`) expose behaviour through getters/setters rather than manipulating raw JDBC rows everywhere.
- **Inheritance & polymorphism** — `Customer`, `Pharmacist`, and `Admin` specialise an abstract `User` with overriding capability checks (`canManageCatalog()`, `canDeleteMedicines()`, `canDeleteUsers()`). Servlets call those methods instead of branching on enums everywhere.
- **Interface polymorphism** — Checkout runs through `PurchaseProcessor`; `JdbcPurchaseProcessor` is swappable behind the same contract for tests.

## Roles

| Role        | Behaviour |
|------------|-----------|
| `CUSTOMER` | Browse catalogue, cart, transactional checkout (`orders` / `order_lines`). |
| `PHARMACIST` | Create, edit, and delete catalogue items (`POST` / `PUT` / `api/medicines`). |
| `ADMIN` | Same catalogue powers as pharmacist, plus moderate user accounts (`GET`/`DELETE /api/admin/users`). Admin signup is **allowed only** when a secret key is set in `db.properties`. |

Default bootstrap admin (created only when `users` is empty):

- Email: `admin@medstore.local`
- Password: `Admin123!`

Change `src/main/java/com/medstore/servlet/AppBootstrapListener.java` or promote users via SQL in production scenarios.

Additional admins may be cloned by inserting a row with hashed password or adjusting `role` in MySQL.

### Optional: allow admin self-registration

In `src/main/resources/db.properties`, set:

`security.admin_registration_key=your-secret`

Then, in the Register form, choose **Administrator** and enter the same key. If the key is empty, admin signup is disabled.

## Database setup

1. Install MySQL 8+ and create the schema:

   ```sql
   CREATE DATABASE medstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. Import tables:

   ```bash
   mysql -u root -p medstore < sql/schema.sql
   ```

3. Edit JDBC settings in `src/main/resources/db.properties`.

## Build & run

Requirements: JDK 17+, Apache Maven, Tomcat **10.1+** (Jakarta Servlet 6).

```bash
mvn clean package
```

Copy `target/medstore.war` into Tomcat’s `webapps/` directory, start the server, then open:

`http://localhost:8080/medstore/`

Session cookies are HTTP-only; the UI calls JSON endpoints under `api/*` with `fetch(..., { credentials: "same-origin" })`.

## API summary

| Method & path | Notes |
|---------------|-------|
| `POST /api/register` | Payload `{email,password,fullName,role}` where `role` is `CUSTOMER` or `PHARMACIST`. |
| `POST /api/login` / `POST /api/logout` | Establishes server session. |
| `GET /api/me` | Returns `{authenticated, user?}`. |
| `PUT/DELETE /api/profile` | Profile edit or self-delete. |
| `GET /api/medicines` | List catalogue (any signed-in role). |
| `POST /api/medicines` | Add item (pharmacist or admin). |
| `PUT /api/medicines?id=` | Update item `{name,description,price,stock}` — pharmacist or admin. |
| `DELETE /api/medicines?id=` | Remove item — pharmacist or admin. |
| `GET|POST|PUT|DELETE /api/cart` | Customer cart (JSON bodies for POST/PUT). |
| `POST /api/checkout` | Customer completes purchase. |
| `GET /api/admin/users` | Lists users (admin). |
| `DELETE /api/admin/users?id=` | Deletes user (cannot remove last admin or yourself). |

## Front-end bundle

Located under `src/main/webapp`:

- `index.html` shell + onboarding copy  
- `css/app.css` design tokens  
- `js/app.js` role-aware SPA navigation against the servlet JSON layer  
