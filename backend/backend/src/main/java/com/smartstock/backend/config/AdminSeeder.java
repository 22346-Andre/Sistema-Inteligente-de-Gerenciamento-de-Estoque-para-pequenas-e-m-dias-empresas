package com.smartstock.backend.config;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Usuario;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Verifica se o SUPER_ADMIN já existe pelo e-mail para não duplicar quando o servidor reiniciar
        if (usuarioRepository.findByEmail("admin@smartstock.com.br").isEmpty()) {
            System.out.println("⚙️ [SISTEMA] Iniciando o 'Big Bang' do SmartStock...");

            // 1. Cria a Empresa HQ (A base da sua equipe de Suporte)
            Empresa hq = new Empresa();
            hq.setRazaoSocial("SmartStock Suporte e Tecnologia LTDA");
            hq.setNomeFantasia("SmartStock HQ");
            hq.setCnpj("00.000.000/0001-00"); // CNPJ exclusivo do sistema
            hq.setEmailContato("suporte@smartstock.com.br");

            empresaRepository.save(hq); // Salva e gera o ID da sua empresa

            // 2. Cria o Usuário Master
            System.out.println("👑 [SISTEMA] Forjando a Chave Mestra (SUPER_ADMIN)...");
            Usuario superAdmin = new Usuario();
            superAdmin.setNome("Suporte Master (André)");
            superAdmin.setEmail("admin@smartstock.com.br");
            superAdmin.setSenha(passwordEncoder.encode("Andre123"));
            superAdmin.setPerfil("SUPER_ADMIN");
            superAdmin.setEmpresa(hq); // Vincula à Empresa HQ

            usuarioRepository.save(superAdmin);

            System.out.println("✅ [SISTEMA] Infraestrutura inicial (Tenant Zero) criada com sucesso!");
        }
    }
}
