package com.smartstock.backend.config;

import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class AdminUserConfig implements CommandLineRunner {

    private UsuarioRepository userRepository;
    private BCryptPasswordEncoder passwordEncoder;

    public AdminUserConfig(UsuarioRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        var userAdmin = userRepository.findByEmail("admin@smartstock.com");

        userAdmin.ifPresentOrElse(
                user -> System.out.println("Admin já existe"),
                () -> {
                    var user = new Usuario();
                    user.setNome("Administrador");
                    user.setEmail("admin@smartstock.com");
                    user.setSenha(passwordEncoder.encode("123456")); // Senha padrão
                    user.setPerfil("ADMIN");
                    userRepository.save(user);
                    System.out.println("Admin criado com sucesso!");
                }
        );
    }
}
