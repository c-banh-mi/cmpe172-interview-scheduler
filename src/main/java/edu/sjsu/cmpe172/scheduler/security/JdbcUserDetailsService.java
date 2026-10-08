package edu.sjsu.cmpe172.scheduler.security;

import edu.sjsu.cmpe172.scheduler.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Called by Spring Security during login. Loads the user's row (with its BCrypt hash and
 * role) over JDBC; Spring then checks the submitted password against the hash.
 */
@Service
public class JdbcUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public JdbcUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
