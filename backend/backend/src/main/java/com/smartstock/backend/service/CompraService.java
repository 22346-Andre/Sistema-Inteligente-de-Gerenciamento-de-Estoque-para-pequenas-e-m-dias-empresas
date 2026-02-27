package com.smartstock.backend.service;

import com.smartstock.backend.dto.SugestaoCompraDTO;
import com.smartstock.backend.model.Empresa;
import com.smartstock.backend.model.Fornecedor;
import com.smartstock.backend.model.Produto;
import com.smartstock.backend.repository.EmpresaRepository;
import com.smartstock.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CompraService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    private Long getEmpresaIdLogada() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("empresaId");
    }

    public List<SugestaoCompraDTO> gerarSugestoesDeCompraWhatsapp() {
        Long empresaId = getEmpresaIdLogada();
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada"));

        // 1. Busca todos os produtos que estão pedindo socorro (estoque baixo)
        List<Produto> criticos = produtoRepository.findProdutosComEstoqueBaixoPorEmpresa(empresaId);

        // 2. Filtra apenas os que têm fornecedor e telefone cadastrados, e agrupa por fornecedor
        Map<Fornecedor, List<Produto>> produtosPorFornecedor = criticos.stream()
                .filter(p -> p.getFornecedor() != null && p.getFornecedor().getTelefone() != null && !p.getFornecedor().getTelefone().isEmpty())
                .collect(Collectors.groupingBy(Produto::getFornecedor));

        List<SugestaoCompraDTO> sugestoes = new ArrayList<>();

        // 3. Monta a mensagem para cada fornecedor
        for (Map.Entry<Fornecedor, List<Produto>> entry : produtosPorFornecedor.entrySet()) {
            Fornecedor fornecedor = entry.getKey();
            List<Produto> produtos = entry.getValue();

            StringBuilder textoZap = new StringBuilder();
            textoZap.append("Olá ").append(fornecedor.getNome()).append(", aqui é da empresa *").append(empresa.getNomeFantasia()).append("*.\n\n");
            textoZap.append("Gostaria de fazer um pedido de reposição para os seguintes itens:\n");

            List<String> nomesDosProdutos = new ArrayList<>();

            for (Produto p : produtos) {
                // Lógica de IA simples: Sugere comprar o dobro do estoque mínimo para ter folga
                int minimo = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 5;
                int sugestaoQtd = minimo * 2;

                textoZap.append("📦 ").append(sugestaoQtd).append(" un. de *").append(p.getNome()).append("*\n");
                nomesDosProdutos.add(p.getNome());
            }

            textoZap.append("\nPode me confirmar o valor total e o prazo de entrega? Fico no aguardo!");

            // 4. Limpa o telefone (tira parênteses, traços, etc) e gera o Link seguro
            String telefoneLimpo = fornecedor.getTelefone().replaceAll("[^0-9]", "");

            // Adiciona o DDI do Brasil (55) se o cliente não tiver digitado
            if (!telefoneLimpo.startsWith("55") && telefoneLimpo.length() <= 11) {
                telefoneLimpo = "55" + telefoneLimpo;
            }

            // Transforma os espaços e quebras de linha no formato de URL da internet (%20, %0A)
            String textoEncodado = URLEncoder.encode(textoZap.toString(), StandardCharsets.UTF_8);
            String linkFinal = "https://wa.me/" + telefoneLimpo + "?text=" + textoEncodado;

            // 5. Preenche a "Caixa de Transporte" (DTO)
            SugestaoCompraDTO dto = new SugestaoCompraDTO();
            dto.setNomeFornecedor(fornecedor.getNome());
            dto.setTelefone(fornecedor.getTelefone());
            dto.setNomesProdutos(nomesDosProdutos);
            dto.setLinkWhatsapp(linkFinal);

            sugestoes.add(dto);
        }

        return sugestoes;
    }
}