package com.smartstock.backend.specification;

import com.smartstock.backend.model.Produto;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProdutoSpecification {

    // 1. O Filtro Obrigatório (Regra do Cercadinho da Empresa)
    public static Specification<Produto> pertenceAEmpresa(Long empresaId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("empresa").get("id"), empresaId);
    }

    // 2. Filtro por Categoria (Busca parcial, ignora maiúsculas/minúsculas)
    public static Specification<Produto> categoriaContem(String categoria) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("categoria")), "%" + categoria.toLowerCase() + "%");
    }

    // 3. Filtro por Intervalo de Preço
    public static Specification<Produto> precoEntre(BigDecimal min, BigDecimal max) {
        return (root, query, criteriaBuilder) -> {
            if (min != null && max != null) {
                return criteriaBuilder.between(root.get("preco"), min, max);
            } else if (min != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("preco"), min);
            } else if (max != null) {
                return criteriaBuilder.lessThanOrEqualTo(root.get("preco"), max);
            }
            return null;
        };
    }

    // 4. Filtro por Data de Atualização (Ex: Produtos alterados desde o dia 10)
    public static Specification<Produto> atualizadoApos(LocalDateTime data) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("dataAtualizacao"), data);
    }
}