ALTER TABLE movimentacoes ADD COLUMN forma_pagamento VARCHAR(20) NULL;
CREATE INDEX idx_movimentacoes_forma_pagamento ON movimentacoes (empresa_id, forma_pagamento);
