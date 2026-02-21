package com.smartstock.backend.service;

import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VerificadorEstoqueService {

    private final ProdutoRepository produtoRepository;
    private final EmailService emailService;

    public VerificadorEstoqueService(ProdutoRepository produtoRepository, EmailService emailService) {
        this.produtoRepository = produtoRepository;
        this.emailService = emailService;
    }

    // Roda a cada 60 segundos
    @Scheduled(fixedRate = 7200000)
    public void verificarNiveisDeEstoque() {
        System.out.println("🔎 Iniciando verificação automática de estoque...");

        List<Produto> produtosCriticos = produtoRepository.findProdutosComEstoqueBaixo();

        if (!produtosCriticos.isEmpty()) {
            enviarRelatorioDeEstoqueBaixo(produtosCriticos);
        } else {
            System.out.println("✅ Verificação concluída: Nenhum produto com estoque baixo.");
        }
    }

    private void enviarRelatorioDeEstoqueBaixo(List<Produto> produtos) {
        StringBuilder conteudo = new StringBuilder();
        conteudo.append("Olá,\n\n");
        conteudo.append("Atenção! Os seguintes produtos atingiram o nível mínimo de estoque e precisam de reposição:\n\n");

        for (Produto p : produtos) {
            conteudo.append("📦 Produto: ").append(p.getNome()).append("\n");
            conteudo.append("   Estoque Atual: ").append(p.getQuantidade()).append("\n");
            conteudo.append("   Mínimo Exigido: ").append(p.getEstoqueMinimo()).append("\n");
            conteudo.append("----------------------------------------\n");
        }

        conteudo.append("\nPor favor, acesse o sistema SmartStock para providenciar as compras.");

        emailService.sendEmail(
                "andrelucasreis2004t@gmail.com",
                "🚨 SMARTSTOCK: Alerta de Estoque Crítico",
                conteudo.toString()
        );

        System.out.println("📧 E-mail de alerta disparado com sucesso para os gestores!");
    }
}
