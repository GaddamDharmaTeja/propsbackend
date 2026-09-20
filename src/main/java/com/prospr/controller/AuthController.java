package com.prospr.controller;
import com.prospr.config.JwtService;
import com.prospr.dto.*;
import com.prospr.model.User;
import com.prospr.model.FamilyMember;
import com.prospr.repository.FamilyMemberRepository;
import com.prospr.repository.UserRepository;
import com.prospr.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@RestController @RequestMapping("/api/auth") @CrossOrigin(origins = "${PROSPR_WEB_ORIGIN:http://localhost:3000}")
public class AuthController {
  private final UserService users; private final UserRepository repository; private final FamilyMemberRepository members; private final PasswordEncoder passwords; private final JwtService jwt;
  public AuthController(UserService users, UserRepository repository, FamilyMemberRepository members, PasswordEncoder passwords, JwtService jwt) { this.users=users; this.repository=repository; this.members=members; this.passwords=passwords; this.jwt=jwt; }
  @PostMapping("/register") public ResponseEntity<AuthResponse> register(@Valid @RequestBody CreateUserRequest request) { User user=users.createUser(request); FamilyMember creator=new FamilyMember(); creator.id=user.getId(); creator.ownerId=user.getId(); creator.householdId=user.getHouseholdId(); creator.accountId=user.getId(); creator.name=(user.getFirstName()+" "+user.getLastName()).trim(); creator.relationship="SELF"; creator.username=user.getUsername(); creator.email=user.getEmail(); creator.gender=user.getGender(); creator.dob=user.getDob(); creator.status="ACTIVE"; members.save(creator); return ResponseEntity.status(HttpStatus.CREATED).body(response(user)); }
  @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    String identity=request.identity().trim().toLowerCase(Locale.ROOT);
    User user=repository.findByEmail(identity).or(() -> repository.findByUsername(identity)).or(() -> repository.findByMobile(identity)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email, username, or password"));
    if (!passwords.matches(request.password(), user.getPassword())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email, username, or password");
    return response(user);
  }
  @PostMapping("/activate") public AuthResponse activate(@Valid @RequestBody ActivationRequest request) {
    FamilyMember member=members.findByActivationTokenHash(hash(request.token().trim())).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"This activation link is invalid or has already been used."));
    String status=member.status==null?"PENDING":member.status;
    if (!"PENDING".equalsIgnoreCase(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This activation link has already been used. Sign in instead, or ask your household creator for a new link.");
    if (request.password().length()<8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use at least 8 characters for your password.");
    if (activationExpired(member)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This activation link has expired. Ask your household creator for a new link.");
    String username=member.username==null?"":member.username.trim().toLowerCase(Locale.ROOT);
    if(username.length()<3||repository.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"This username is no longer available. Ask your household creator to update it.");
    User user=new User(); user.setId(UUID.randomUUID().toString()); user.setFirstName(first(member.name)); user.setLastName(rest(member.name)); user.setUsername(username); user.setEmail(blankToNull(member.email)); user.setPassword(passwords.encode(request.password())); user.setGender(member.gender); user.setDob(member.dob); user.setHouseholdId(member.householdId); user.setMemberId(member.id); user.setHouseholdCreator(false); user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now()); repository.save(user);
    member.accountId=user.getId(); member.status="ACTIVE"; member.activationTokenHash=null; member.activationExpiresAt=null; member.updatedAt=LocalDateTime.now(); members.save(member); return response(user);
  }
  @GetMapping("/me") public AuthResponse.UserProfile me(@AuthenticationPrincipal String userId) { return profile(repository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))); }
  private AuthResponse response(User user) { return new AuthResponse(jwt.create(user.getId(), user.getEmail()), profile(user)); }
  private AuthResponse.UserProfile profile(User u) { return new AuthResponse.UserProfile(u.getId(),u.getMemberId(),u.getHouseholdId(),(u.getFirstName()+" "+u.getLastName()).trim(),u.getUsername(),u.getEmail(),u.getMobile(),u.getGender(),u.isHouseholdCreator()); }
  private static String first(String name){String[] parts=(name==null?"":name.trim()).split("\\s+",2);return parts.length==0||parts[0].isBlank()?"Family":parts[0];}
  private static String rest(String name){String[] parts=(name==null?"":name.trim()).split("\\s+",2);return parts.length<2?"":parts[1];}
  private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim().toLowerCase(Locale.ROOT);}
  private static boolean activationExpired(FamilyMember member){
    if (member.activationExpiryEpoch!=null && member.activationExpiryEpoch>0) return member.activationExpiryEpoch<System.currentTimeMillis();
    LocalDateTime expires=member.activationExpiresAt;
    if (expires==null || expires.getYear()<2024) return false;
    return expires.isBefore(LocalDateTime.now());
  }
  private static String hash(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
