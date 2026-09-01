package com.raisetimeline.backend.auth;

import com.raisetimeline.backend.security.JwtService;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new DuplicateUserException("username is already taken");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new DuplicateUserException("email is already registered");
		}

		String displayName = request.displayName() != null && !request.displayName().isBlank()
				? request.displayName()
				: request.username();
		User user = new User(request.username(), request.email(), passwordEncoder.encode(request.password()), displayName);
		User saved = userRepository.save(user);

		String token = jwtService.generateToken(saved.getId(), saved.getUsername());
		return new AuthResponse(token, saved.getId(), saved.getUsername());
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new InvalidCredentialsException("invalid email or password"));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException("invalid email or password");
		}

		String token = jwtService.generateToken(user.getId(), user.getUsername());
		return new AuthResponse(token, user.getId(), user.getUsername());
	}
}
