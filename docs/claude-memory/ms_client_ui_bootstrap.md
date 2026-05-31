---
name: ms-client-ui-bootstrap
description: "ms-client UI redesign — Bootstrap 5.3 dark theme with vivid violet/cyan palette, animated navbar, role-based nav links via sec:authorize, and layout fragment pattern"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3db72030-c893-4c1b-bc2c-b2765fe06257
---

Commit `d6650a5`: complete ms-client UI overhaul using Bootstrap 5.3 (`data-bs-theme="dark"`).

**Why:** replace the minimal hand-rolled CSS with a modern dark theme — vivid violet `#8b5cf6` / cyan `#22d3ee`, page fade-up animation, navbar blur + brand gradient animation.

**Architecture:**
- `layout.html` provides two fragments: `head(title)` (Bootstrap CSS + app.css) and `header` (navbar). Pages use `<head th:replace="~{layout :: head('Title')}">` and `<div th:replace="~{layout :: header}">`.
- Navbar uses `sec:authorize="hasRole('ADMIN')"` (thymeleaf-extras-springsecurity6) to show/hide the Consumer link — reads from the security context, no model attribute needed.
- `sec:authentication="name"` displays the username in the navbar without a model attribute.
- `login.html` and `public.html` are standalone (no layout dependency) — they include Bootstrap CSS/JS directly.

**Dependency added:** `thymeleaf-extras-springsecurity6` in ms-client/pom.xml (version managed by Spring Boot, no explicit version).

**`sec:authorize` namespace:** declared on the `<html>` tag of `layout.html` — it propagates to all fragments. Pages that use `sec:` directly in their own content must also declare `xmlns:sec="http://www.thymeleaf.org/extras/spring-security"`.

**home.html Consumer card:** uses `th:if="${roles.contains('ROLE_ADMIN')}"` (model attribute) rather than `sec:authorize` — avoids needing the sec namespace on home.html since `roles` is already in the HomeController model.

**How to apply:** when adding new pages to ms-client, use the `head(title)` + `header` fragments; add Bootstrap JS at the end of `<body>`. For role-gated nav items, add `sec:authorize` directly in `layout.html`'s header fragment.
