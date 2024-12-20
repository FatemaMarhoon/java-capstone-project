package com.example.Project4.service;



import com.example.Project4.dto.ProfileDto;
import com.example.Project4.dto.RoleDto;
import com.example.Project4.dto.UserDto;
import com.example.Project4.enums.Status;
import com.example.Project4.model.*;
import com.example.Project4.repository.OrganizationRepository;
import com.example.Project4.repository.ProfileRepository;
import com.example.Project4.repository.RoleRepository;
import com.example.Project4.repository.UserRepository;
import com.example.Project4.security.JWTUtils;
import com.example.Project4.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import com.example.Project4.dao.GenericDao;



import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import net.bytebuddy.utility.RandomString;
/**
 * Service class for managing user-related operations including registration,
 * authentication, profile management, and password reset.
 */

@Service
public class UserService {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RoleRepository roleRepository;
   private final OrganizationRepository organizationRepository ;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtils jwTutils;
    private final AuthenticationManager authenticationManager;
    private MyUserDetails myUserDetails;
    private final JavaMailSender mailSender;

    public User user;
    @Autowired
    public UserService(UserRepository userRepository,
                       ProfileRepository profileRepository,
                       RoleRepository roleRepository,
                       OrganizationRepository organizationRepository,

                       @Lazy PasswordEncoder passwordEncoder,
                       JWTUtils jwTutils,
                       @Lazy AuthenticationManager authenticationManager,
                       @Lazy MyUserDetails myUserDetails,
                       JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.organizationRepository=organizationRepository;
        this.profileRepository = profileRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwTutils = jwTutils;
        this.authenticationManager = authenticationManager;
        this.myUserDetails = myUserDetails;
        this.mailSender = mailSender;
    }
    /**
     * Retrieves a user by ID and optionally includes details.
     *
     * @param uid the ID of the user
     * @param details whether to include user details
     * @return UserDto containing user information
     */
    public UserDto getById(Long uid, Boolean details) {
        return userRepository.findById(uid).map(user -> new UserDto(user, details)).orElse(null);
    }
    /**
     * Retrieves a user by email and optionally includes details.
     *
     * @param email the email of the user
     * @param details whether to include user details
     * @return UserDto containing user information
     */
    public UserDto getUserByEmail(String email, Boolean details) {
        return userRepository.findUserByEmail(email).map(user -> new UserDto(user, details)).orElse(null);
    }
    /**
     * Retrieves users by status and optionally includes details.
     *
     * @param status the status of the users
     * @param details whether to include user details
     * @return list of UserDto containing user information
     */
    public List<UserDto> getByStatus(Boolean status, Boolean details) {
        return userRepository.findByStatus(status).stream().map(user -> new UserDto(user, details)).toList();
    }
    /**
     * Retrieves all users and optionally includes details.
     *
     * @param details whether to include user details
     * @return list of UserDto containing user information
     */
    public List<UserDto> getAll(Boolean details) {
        return userRepository.findAll().stream().map(user -> new UserDto(user, details)).toList();
    }
    /**
     * Creates a new user with the provided UserDto. Performs validation and saves
     * the user and associated entities.
     *
     * @param dto the UserDto containing user information
     * @return GenericDao containing UserDto and potential errors
     * @throws MessagingException if an error occurs while sending an email
     * @throws UnsupportedEncodingException if an encoding error occurs
     */
    public GenericDao<UserDto> createUser(UserDto dto) throws MessagingException, UnsupportedEncodingException {
        GenericDao<UserDto> returnDao = new GenericDao<>();
        List<String> errors = new ArrayList<>();

        // Log received UserDto
        if (dto.getOrganizationDto() != null) {
            System.out.println("Received UserDto: " + dto.getOrganizationDto().getName());
        } else {
            System.out.println("No organization data provided.");
        }

        // Validate Email
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            errors.add("Email cannot be empty");
        }

        // Password validation
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            errors.add("Password cannot be empty");
        } else {
            String password = dto.getPassword();
            if (password.length() < 8) {
                errors.add("Password must be at least 8 characters long");
            }
            if (!Pattern.compile("[A-Z]").matcher(password).find()) {
                errors.add("Password must contain at least one uppercase letter");
            }
            if (!Pattern.compile("[a-z]").matcher(password).find()) {
                errors.add("Password must contain at least one lowercase letter");
            }
            if (!Pattern.compile("[0-9]").matcher(password).find()) {
                errors.add("Password must contain at least one number");
            }
            if (!Pattern.compile("[^a-zA-Z0-9]").matcher(password).find()) {
                errors.add("Password must contain at least one special character (e.g., @, #, $, etc.)");
            }
        }

        // Validate First Name
        if (dto.getFirstName() == null || dto.getFirstName().isBlank()) {
            errors.add("First Name cannot be empty");
        }

        // Validate Last Name
        if (dto.getLastName() == null || dto.getLastName().isBlank()) {
            errors.add("Last Name cannot be empty");
        }

        // Set default role based on user type if role is null
        if (dto.getRole() == null || dto.getRole().getId() == null) {
            if (dto.isOrganization()) {
                dto.setRole(new RoleDto(3L));  // Default to 'Organization' role ID
            } else if (dto.isVolunteer()) {
                dto.setRole(new RoleDto(2L));  // Default to 'Volunteer' role ID
            } else {
                dto.setRole(new RoleDto(1L));  // Default to 'Admin' role ID
            }
        }

        // Validate if the provided role exists in the database
        Optional<Role> role = roleRepository.findById(dto.getRole().getId());
        if (role.isEmpty()) {
            errors.add("Role does not exist");
        }

        // Validate Organization Details if the user is an organization
        if (dto.isOrganization()) {
            if (dto.getOrganizationDto() == null) {
                errors.add("Organization details must be provided when isOrganization is true.");
            } else {
                if (dto.getOrganizationDto().getName() == null || dto.getOrganizationDto().getName().isBlank()) {
                    errors.add("Organization name cannot be empty.");
                }
                // Add other organization validations as necessary
            }
        }

        // Proceed with user creation if there are no errors
        if (errors.isEmpty()) {
            Optional<User> retrievedUser = userRepository.findUserByEmail(dto.getEmail());
            if (retrievedUser.isEmpty()) {
                dto.setStatus(true);

                // Save user profile
                UserProfile profile = new UserProfile();
                profile.setFirstName(dto.getFirstName());
                profile.setLastName(dto.getLastName());
                profile.setProfilePic("");
                profile = profileRepository.save(profile);  // Save profile first to avoid detached entity issue

                // Encode password and set UserDto fields
                dto.setPassword(passwordEncoder.encode(dto.getPassword()));
                dto.setProfile(new ProfileDto(profile, false));
                dto.setRole(new RoleDto(role.get(), false));  // Set full Role object after validating
                String randomCode = RandomString.make(64);
                dto.setVerificationCode(randomCode);
                dto.setEnabled(false);

                // Create User and save
                User user = new User(dto);
                user.setProfile(profile);  // Link profile to user
                user.setRole(role.get());  // Link role to user
                user.setOrganization(dto.isOrganization());
                user.setVolunteer(dto.isVolunteer());
                User savedUser = userRepository.save(user);

                // Create and save the Organization entity if necessary
                if (dto.isOrganization() && dto.getOrganizationDto() != null) {
                    Organization organization = new Organization();
                    organization.setName(dto.getOrganizationDto().getName());
                    organization.setContactInfo(dto.getOrganizationDto().getContactInfo());
                    organization.setDescription(dto.getOrganizationDto().getDescription());
                    organization.setUser(savedUser); // Link organization to user
                    organization.setStatus(Status.PENDING);

                    organizationRepository.save(organization); // Save organization
                    System.out.println("Organization created: " + dto.getOrganizationDto().getName());
                }

                returnDao.setObject(new UserDto(savedUser, false));
                sendVerificationEmail(savedUser);
            } else {
                errors.add("User already exists");
            }
        }

        // Handle errors if any
        if (!errors.isEmpty()) {
            System.out.println("Errors: " + errors);
            returnDao.setErrors(errors);
        }

        return returnDao;
    }



    /**
     * Edits an existing user with the provided UserDto.
     *
     * @param dto the UserDto containing updated user information
     * @return GenericDao containing UserDto and potential errors
     */
    public GenericDao<UserDto> editUser(UserDto dto) {
        GenericDao<UserDto> returnDao = new GenericDao<>();

        List<String> errors = new ArrayList<>();

        if (dto.getId() == null) {
            errors.add("User ID cannot be empty");
        }

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            errors.add("Email cannot be empty");
        }

        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            errors.add("Password cannot be empty");
        }


        if (errors.isEmpty()) {
            Optional<User> retrievedUser = userRepository.findById(dto.getId());

            if (retrievedUser.isPresent()) {
                retrievedUser.get().setEmail(dto.getEmail());
                retrievedUser.get().setPassword(dto.getPassword());

                User savedUser = userRepository.save(retrievedUser.get());

                returnDao.setObject(new UserDto(savedUser, false));
            } else {
                errors.add("User does not exist");
            }
        }

        if (!errors.isEmpty()) {
            returnDao.setErrors(errors);
        }

        return returnDao;
    }

    public GenericDao<Boolean> deleteUser(Long uid) {
        Optional<User> retrievedUser = userRepository.findById(uid);

        if (retrievedUser.isPresent()) {
            retrievedUser.get().setStatus(false);
            userRepository.save(retrievedUser.get());
            return new GenericDao<>(true, null);
        } else {
            return new GenericDao<>(false, List.of("User does not exist"));
        }
    }

    public Optional<User> findUserByEmailAddress(String email) {
        return userRepository.findUserByEmail(email);
    }

    public ResponseEntity<?> loginUser(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(), loginRequest.getPassword());
        try {
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            MyUserDetails myUserDetails = (MyUserDetails) authentication.getPrincipal();

            // Retrieve the user from the database
            Optional<User> user = userRepository.findUserByEmail(loginRequest.getEmail());

            // Check if the user is enabled and the account is not deactivated
            if (user.isPresent()) {
                User spUser = user.get();

                if (!spUser.isEnabled()) {
                    return ResponseEntity.status(403).body(new LoginResponse("Account is not verified. Please check your email for the verification link."));
                } else if (!spUser.getStatus()) {
                    return ResponseEntity.status(403).body(new LoginResponse("Account is deactivated. Please contact support."));
                } else if (spUser.isOrganization()&&!"APPROVED".equalsIgnoreCase(String.valueOf(spUser.getOrganization().getStatus()))) {
                    return ResponseEntity.status(403).body(new LoginResponse("Organization is still in review. Please contact support."));
                }
                else {
                    final String jwt = jwTutils.generateJwtToken(myUserDetails);
                    return ResponseEntity.ok(new LoginResponse(jwt));
                }
            } else {
                // User not found
                return ResponseEntity.status(403).body(new LoginResponse("Account is not verified. Please check your email for the verification link."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new LoginResponse("Error! Username or Password is incorrect"));
        }
    }


    public static User getCurrentLoggedInUser() {
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userDetails.getUser();
    }

    private void sendVerificationEmail(User user)
            throws MessagingException, UnsupportedEncodingException {
        String toAddress = user.getEmail();
        String fromAddress = "spendingapp1@gmail.com";
        String senderName = "Volunteer Matching System";
        String subject = "Please verify your registration";
        String content = "Dear [[name]],<br>"
                + "Please use the following code to verify your registration:<br>"
                + "<h3>[[CODE]]</h3>"
                + "Thank you,<br>"
                + "Volunteer Matching System";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);

        helper.setFrom(fromAddress, senderName);
        helper.setTo(toAddress);
        helper.setSubject(subject);

        content = content.replace("[[name]]", user.getEmail());
        content = content.replace("[[CODE]]", user.getVerificationCode());

        helper.setText(content, true);

        mailSender.send(message);
    }

    public boolean verify(String verificationCode) {
        Optional<User> user = userRepository.findByVerificationCode(verificationCode);

        if (user.isEmpty() || user.get().isEnabled()) {
            return false;
        } else {
            user.get().setVerificationCode(null);
            user.get().setEnabled(true);
            userRepository.save(user.get());

            return true;
        }

    }

    public void sendPasswordResetEmail(User user, String resetToken) throws MessagingException, UnsupportedEncodingException {
        String toAddress = user.getEmail();
        String fromAddress = "spendingapp1@gmail.com";
        String senderName = "Volunteer Matching System";
        String subject = "Password Reset Request";
        String content = "Dear [[name]],<br>"
                + "We received a request to reset your password. Use the following code to reset your password:<br>"
                + "<h3>Reset Code: [[CODE]]</h3>"
                + "If you did not request this, please ignore this email.<br>"
                + "Thank you,<br>"
                + "Volunteer Matching System";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);

        helper.setFrom(fromAddress, senderName);
        helper.setTo(toAddress);
        helper.setSubject(subject);

        content = content.replace("[[name]]", user.getEmail());
        content = content.replace("[[CODE]]", resetToken);

        helper.setText(content, true);

        mailSender.send(message);
    }


    public String generatePasswordResetToken() {
        return RandomString.make(64);
    }

    public boolean resetPassword(String token, String newPassword) {
        Optional<User> user = userRepository.findByResetToken(token);
        if (user.isEmpty() || user.get().getTokenExpirationTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        user.get().setPassword(passwordEncoder.encode(newPassword));
        user.get().setResetToken(null);
        user.get().setTokenExpirationTime(null);
        userRepository.save(user.get());
        return true;
    }

    public boolean requestPasswordReset(String email) {
        Optional<User> user = findUserByEmailAddress(email);
        if (user.isEmpty()) {
            return false;
        }

        String resetToken = generatePasswordResetToken();
        user.get().setResetToken(resetToken);
        user.get().setTokenExpirationTime(LocalDateTime.now().plusHours(1)); // Token valid for 1 hour
        userRepository.save(user.get());

        try {
            sendPasswordResetEmail(user.get(), resetToken);
        } catch (MessagingException | UnsupportedEncodingException e) {
            return false;
        }

        return true;
    }


    public boolean changePassword(String oldPassword, String newPassword) {
        User currentUser = getCurrentLoggedInUser();
        if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
            return false;
        }

        currentUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentUser);
        return true;
    }


    /**
     * Retrieves the role of the currently logged-in user.
     *
     * @return the role of the currently logged-in user, or null if no user is authenticated.
     */
    public Role getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof MyUserDetails) {
            MyUserDetails userDetails = (MyUserDetails) authentication.getPrincipal();
            User currentUser = userDetails.getUser();
            return currentUser.getRole();
        }

        return null; // No user is authenticated
    }
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof MyUserDetails) {
            MyUserDetails userDetails = (MyUserDetails) authentication.getPrincipal();
            User currentUser = userDetails.getUser();
            this.user=currentUser;
            return currentUser;
        }

        // Logging for debugging purposes
        System.out.println("Authentication is null or principal is not MyUserDetails");

        return null; // No user is authenticated
    }


    public Organization getCurrentUserOrg() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof MyUserDetails) {
            MyUserDetails userDetails = (MyUserDetails) authentication.getPrincipal();
            User currentUser = userDetails.getUser();
            return currentUser.getOrganization().getUser().getOrganization();
        }

        return null; // No user is authenticated
    }
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof MyUserDetails) {
            MyUserDetails userDetails = (MyUserDetails) authentication.getPrincipal();
            User currentUser = userDetails.getUser();
            return currentUser.getId();
        }

        // Logging for debugging purposes
        System.out.println("Authentication is null or principal is not MyUserDetails");

        return null; // No user is authenticated
    }
}
