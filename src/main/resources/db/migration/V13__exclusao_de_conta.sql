-- Etapa 19: a pessoa exclui a própria conta (exigência da App Store). O login é apagado: o e-mail vira um endereço
-- que não existe e a senha deixa de valer. O registro fica, para o histórico (quem lançou, quem cancelou) continuar
-- apontando para alguém; o cadastro no condomínio (pessoa, cobranças) fica com o condomínio.
ALTER TABLE usuario ADD COLUMN excluido_em timestamptz;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_excluido_inativo CHECK (excluido_em IS NULL OR NOT ativo);
