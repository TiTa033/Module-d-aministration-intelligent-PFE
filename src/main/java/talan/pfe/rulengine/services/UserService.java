//package talan.pfe.moduleadministration.services;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.core.userdetails.User;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Service;
//import talan.pfe.moduleadministration.dtos.request.CreateUserRequest;
//import talan.pfe.moduleadministration.repositories.UserRepository;
//
//import java.util.List;
//@Service @RequiredArgsConstructor
//public class UserService {
//    private final UserRepository userRepository;
//    private final PasswordEncoder passwordEncoder;
//    public User create(CreateUserRequest req, Long tenantId) {
//        return userRepository.save(User.builder()
//                .email(req.getEmail()).passwordHash(passwordEncoder.encode(req.getPassword()))
//                .role(req.getRole()).tenantId(tenantId).build());
//    }
//    public User getById(Long id) { return userRepository.findById(id).orElseThrow(); }
//    public List<User> getByTenant(Long tenantId) { return userRepository.findAllByTenantId(tenantId); }
//    public void delete(Long id) { userRepository.deleteById(id); }
//}
