-- O UniqueConstraint (codigo_barras, empresa_id) que já existe só é útil
-- pra buscas que também filtram por codigo_barras (regra do prefixo do
-- MySQL: índice composto só serve pra busca pela coluna da esquerda
-- primeiro). Relatórios que filtram só por empresa_id
-- (findProdutosOrdenadosPorValorTotal, findProdutosEncalhados,
-- countByEmpresaId, calcularValorTotalEstoque etc.) não usam esse índice e
-- caem em table scan à medida que a base de produtos cresce, especialmente
-- em empresas com catálogo grande.
CREATE INDEX idx_produtos_empresa_id ON produtos (empresa_id);

-- Movimentacao também é consultada sempre por empresa_id (dashboard,
-- relatórios, PDV) e ainda mais por data dentro da empresa -- índice
-- composto cobre os dois padrões de acesso mais comuns de uma vez.
CREATE INDEX idx_movimentacoes_empresa_data ON movimentacoes (empresa_id, data_movimentacao);
