package dev.lucasvital.auth.user;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticatedUser CurrentUser currentUser) {
        // Defensivo: nenhum endpoint deleta usuarios hoje, mas o token continua valido
        // ate expirar mesmo que a linha seja removida manualmente do banco.
        User user =
                userRepository
                        .findById(currentUser.userId())
                        .orElseThrow(InvalidAccessTokenException::new);

        return UserResponse.from(user);
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticatedUser CurrentUser currentUser) {
        if (currentUser.role() != Role.ADMIN) {
            throw new ForbiddenRoleException();
        }

        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }
}
