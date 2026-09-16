package com.Finn.everything_app.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * {@link org.springframework.security.core.userdetails.UserDetails} mit den beiden Angaben, die
 * Spring Securitys Standardklasse nicht kennt, der {@link JwtAuthenticationFilter} aber braucht:
 * die eigene {@code userId} und die {@code tokenVersion}.
 *
 * <p>Absicht dahinter: die Pruefung des Widerrufs-Stands soll <strong>keine</strong> zweite
 * Datenbankabfrage pro Anfrage kosten. Der Filter laedt den Nutzer ohnehin schon ueber den
 * {@link org.springframework.security.core.userdetails.UserDetailsService}; die tokenVersion
 * faehrt in derselben Abfrage mit.
 */
public class AppUserDetails extends org.springframework.security.core.userdetails.User {

    private final transient Long userId;
    private final int tokenVersion;

    public AppUserDetails(String username, String password, Long userId, int tokenVersion,
                          Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.tokenVersion = tokenVersion;
    }

    public Long getUserId() {
        return userId;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
