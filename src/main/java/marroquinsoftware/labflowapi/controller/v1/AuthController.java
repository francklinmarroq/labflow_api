package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.payload.JwtResponse;
import marroquinsoftware.labflowapi.payload.LoginRequest;
import marroquinsoftware.labflowapi.payload.RegisterRequest;
import marroquinsoftware.labflowapi.security.JwtUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final JdbcUserDetailsManager userDetailsManager;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtils jwtUtils,
                          JdbcUserDetailsManager userDetailsManager,
                          BCryptPasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userDetailsManager = userDetailsManager;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtils.generateToken(userDetails);
        return ResponseEntity.ok(new JwtResponse(token, userDetails.getUsername()));
    }

    @PostMapping("/register")
    public ResponseEntity<JwtResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userDetailsManager.userExists(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        UserDetails newUser = User.withUsername(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles("user")
                .build();
        userDetailsManager.createUser(newUser);
        String token = jwtUtils.generateToken(newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(new JwtResponse(token, newUser.getUsername()));
    }
}
