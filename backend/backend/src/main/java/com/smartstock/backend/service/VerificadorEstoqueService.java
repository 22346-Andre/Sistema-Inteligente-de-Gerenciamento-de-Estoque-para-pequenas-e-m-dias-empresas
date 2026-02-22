package com.smartstock.backend.service;

import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VerificadorEstoqueService {

    private final ProdutoRepository produtoRepository;
    private final EmailService emailService;

    public VerificadorEstoqueService(ProdutoRepository produtoRepository, EmailService emailService) {
        this.produtoRepository = produtoRepository;
        this.emailService = emailService;
    }

    // Roda a cada 2 horas (7200000 ms)
    @Scheduled(fixedRate = 7200000)
    public void verificarNiveisDeEstoque() {
        System.out.println("🔎 Iniciando verificação automática de estoque...");

        List<Produto> produtosCriticos = produtoRepository.findProdutosComEstoqueBaixo();

        if (!produtosCriticos.isEmpty()) {

            // MÁGICA SAAS: Agrupa os produtos por empresa!
            Map<Empresa, List<Produto>> produtosPorEmpresa = produtosCriticos.stream()
                    .collect(Collectors.groupingBy(Produto::getEmpresa));

            // Dispara um e-mail para cada empresa encontrada
            for (Map.Entry<Empresa, List<Produto>> entry : produtosPorEmpresa.entrySet()) {
                Empresa empresa = entry.getKey();
                List<Produto> produtos = entry.getValue();

                enviarRelatorioDeEstoqueBaixo(empresa, produtos);
            }

        } else {
            System.out.println("✅ Verificação concluída: Nenhum produto com estoque baixo.");
        }
    }

    private void enviarRelatorioDeEstoqueBaixo(Empresa empresa, List<Produto> produtos) {
        StringBuilder conteudo = new StringBuilder();
        conteudo.append("Olá, gestor da ").append(empresa.getNomeFantasia()).append("!\n\n");
        conteudo.append("Atenção! Os seguintes produtos atingiram o nível mínimo de estoque e precisam de reposição:\n\n");

        for (Produto p : produtos) {
            conteudo.append("📦 Produto: ").append(p.getNome()).append("\n");
            conteudo.append("   Estoque Atual: ").append(p.getQuantidade());
            if(p.getUnidade() != null) conteudo.append(" ").append(p.getUnidade()); // Coloquei a unidade aqui pra ficar chique!
            conteudo.append("\n");
            conteudo.append("   Mínimo Exigido: ").append(p.getEstoqueMinimo()).append("\n");
            conteudo.append("----------------------------------------\n");
        }

        conteudo.append("\nPor favor, acesse o sistema SmartStock para providenciar as compras.");

        //  Pega o e-mail real do gestor daquela empresa específica
        String emailDestino = empresa.getEmailContato();

        // Só tenta enviar se a empresa tiver um e-mail cadastrado
        if (emailDestino != null && !emailDestino.trim().isEmpty()) {
            emailService.sendEmail(
                    emailDestino,
                    "🚨 SMARTSTOCK: Alerta de Estoque Crítico - " + empresa.getNomeFantasia(),
                    conteudo.toString()
            );
            System.out.println("📧 E-mail de alerta disparado com sucesso para o gestor: " + emailDestino);
        } else {
            System.out.println("⚠️ ALERTA: A empresa " + empresa.getNomeFantasia() + " tem produtos acabando, mas não tem e-mail de contato cadastrado!");
        }
    }
}