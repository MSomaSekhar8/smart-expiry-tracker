package com.pantrytracker.auth;

import com.pantrytracker.user.User;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The principal Spring Security carries for an authenticated request. */
public record AuthenticatedUser(User user) {

    public List<SimpleGrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    public String id() {
        return user.getId().toString();
    }
}