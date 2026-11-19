# Etapa 18 — Notificações: no app, por e-mail e push

- **Branch:** `feature/notificacoes` (criada a partir de `desenvolvimento`)
- **Concluída em:** 19/11/2026

## Objetivo

Cada pessoa fica sabendo do que importa para ela sem precisar abrir o app: boleto disponível, pagamento confirmado,
reserva confirmada, fatura do Condomix atrasada, documento novo... O aviso aparece **no app** (o sino) e chega também
**por e-mail** e **push**, conforme a escolha da pessoa. O convite de acesso passa a ir por e-mail.

## Decisões (tomadas com você no início da etapa)

| Tema | Decisão |
|---|---|
| Fila | **Eventos guardados no próprio Postgres** pelo Spring Modulith (tabela `event_publication`). Se o servidor cair ou algo falhar, o evento é reprocessado. O RabbitMQ entra quando houver mais de um servidor ou volume alto: é troca de configuração |
| E-mail | **SMTP genérico**: funciona com qualquer provedor (Amazon SES, Brevo, Resend...). Em desenvolvimento, o **Mailpit** (no `compose.yaml`) recebe tudo e mostra em http://localhost:8025, sem enviar de verdade |
| Push | **Web Push** do próprio PWA (padrão da web, sem conta em serviço externo). Funciona no Android e no iPhone com o app instalado na tela inicial. O push nativo (Firebase) vem com os apps das lojas, na etapa 19 |
| Canais | **No app (o sino), sempre**; e-mail e push, cada pessoa liga ou desliga por assunto |
| Avisos | Os quatro grupos: cobranças, reservas, assinatura do Condomix (só a gestão) e documentos, mais o convite por e-mail |

## O que é avisado

| Grupo | Quando | Para quem |
|---|---|---|
| **Cobranças** | Cobrança nova (com o boleto ligado, quando o boleto e o Pix ficam prontos); **3 dias antes do vencimento**; pagamento confirmado (pelo boleto/Pix ou baixa do síndico) | Proprietário e inquilino da unidade |
| **Reservas** | Reserva confirmada (inclusive feita pela gestão); **lembrete na véspera**; reserva cancelada (com o motivo e se o aluguel continua) | Proprietário, inquilino e morador da unidade |
| **Assinatura do Condomix** | Fatura disponível; fatura vencida (no dia seguinte, com a data da suspensão); acesso suspenso e liberado; **teste grátis acabando** (7 dias antes) | Síndico e administradora |
| **Documentos** | Documento novo publicado | Todos do condomínio |
| **Convite** | Ao convidar alguém (morador, síndico do cliente novo): e-mail com o botão "Criar minha senha". A tela ainda mostra o link, caso o e-mail não chegue | A pessoa convidada |

Os **lembretes do dia** (vencimento, véspera da reserva, fatura vencida, fim do teste) rodam às 8h
(`condomix.lembretes.horario`).

## Como funciona

1. O módulo publica o que aconteceu, por exemplo `CobrancaDisponivel`, `ReservaFeita` ou `FaturaAtrasada`.
   O evento é guardado na mesma transação.
2. Um ouvinte da comunicação, em segundo plano, cria uma **notificação** para cada destinatário.
   - Os destinatários vêm de `DestinatariosDeNotificacoes`, no acesso: só logins ativos e não bloqueados.
   - Cada acontecimento tem uma **chave**: o mesmo acontecimento nunca notifica a mesma pessoa duas vezes, nem
     com o evento reprocessado ou o lembrete rodando de novo.
3. Para cada canal que a pessoa não desligou, fica uma **entrega pendente**. A `EntregaDeNotificacoes` envia a cada
   15 segundos, fora da transação.
   - Se falhar, tenta de novo em 1 min, 5 min, 30 min e 2 h; depois, desiste.
   - Push sem nenhum aparelho inscrito é descartado, sem contar como erro.
   - Aparelho que não existe mais (resposta 404 ou 410 do serviço de push) tem a inscrição apagada.
4. **Web Push sem biblioteca externa** (`CriptografiaWebPush`):
   - a mensagem é cifrada para o aparelho (RFC 8291) e o pedido é assinado com a chave VAPID do Condomix (RFC 8292);
   - as chaves VAPID são geradas no primeiro uso e guardadas no banco, com a privada cifrada pelo Cofre.

## Telas

| Tela | O que mudou |
|---|---|
| **Sino** (no alto, no celular e no computador) | O número de não lidas, conferido a cada minuto |
| **Notificações** (`/notificacoes`) | As 50 mais recentes deste condomínio. A não lida fica destacada. Tocar abre a tela do assunto (ex.: Meus débitos) e marca como lida. Também tem "Marcar todas como lidas" |
| **Conta → Notificações** | Por assunto, liga ou desliga o **e-mail** e o **push**; o grupo da assinatura só aparece para a gestão. Também tem o botão **Ligar o push neste aparelho**, que explica quando não dá (navegador sem suporte, iPhone sem o app instalado, notificações bloqueadas, ambiente de desenvolvimento) |
| **Convites** (Moradores, síndico do cliente) | "Convite enviado por e-mail para... Se não chegar (veja também o spam), envie este link" |

## Arquitetura (SOLID)
- **`CanalDeNotificacao`** (O, L, D): `CanalEmail` e `CanalWebPush`; o WhatsApp entra como mais uma implementação.
  O teste de contrato roda contra os dois: entrega com destino, e "provedor fora do ar" vira `FalhaNaEntrega`, para
  tentar de novo.
- **`DestinatariosDeNotificacoes`** (I): a porta do acesso com só o que a comunicação precisa: quem paga e quem usa a
  unidade, a gestão, todos do condomínio.
- **`EnvioDeEmail`** fica no `compartilhado`, com a implementação SMTP na comunicação. Assim o acesso também manda o
  convite sem criar ciclo entre os módulos (a comunicação já depende do acesso).
- **Eventos públicos novos:** `CobrancaDisponivel`, `CobrancaPaga` e `CobrancaVenceEmBreve` (financeiro);
  `ReservaFeita`, `ReservaDesfeita` e `ReservaAmanha` (reservas); `FaturaDisponivel`, `FaturaAtrasada`,
  `AcessoSuspensoPorFaltaDePagamento`, `AcessoReativadoAposPagamento` e `TesteGratisTerminando` (faturamento). O
  `DocumentoPublicado` já existia.
- **Endereço `/api/notificacoes`**: o `/api/avisos/**` é dos avisos (webhooks) dos gateways, que são abertos.

## Configuração de produção
| Variável | Para quê |
|---|---|
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | O SMTP do provedor de e-mail. Sem o host, o e-mail fica desligado (só o app e o push) |
| `CONDOMIX_EMAIL_REMETENTE` | Quem aparece como remetente (ex.: `Condomix <avisos@condomix.com.br>`); o domínio precisa estar verificado no provedor |
| `CONDOMIX_ENDERECO_DO_APP` | Onde o app está (ex.: `https://app.condomix.com.br`): os links dos e-mails e do push |
| `CONDOMIX_ENDERECO_CONVITE` | O link do convite (ex.: `https://app.condomix.com.br/convite`), como antes |
| `CONDOMIX_CONTATO_PUSH` | Contato exigido pelos serviços de push (ex.: `mailto:suporte@condomix.com.br`) |

O push precisa do app em **https** (o navegador exige) e da `CONDOMIX_COFRE_CHAVE`, que cifra a chave VAPID.

## Como verificar

```bash
./mvnw test                  # backend: 146 testes (NotificacoesTests, contrato dos canais, criptografia do push, e-mail SMTP)
cd frontend && npm test      # frontend: 35 testes
```

Em desenvolvimento, os e-mails aparecem em http://localhost:8025 (Mailpit, sobe com o `compose.yaml`).

### Verificado no navegador (banco e Mailpit temporários, computador e celular)
1. **E-mail:** o síndico lançou uma cobrança de gás de R$ 87,40 para a A-101.
   - A primeira entrega por e-mail falhou, por um erro de montagem do e-mail que os testes não pegavam (corrigido, e
     agora há um teste que monta o e-mail de verdade).
   - A nova tentativa entregou sozinha.
   - O Mailpit mostrou "Olá, Ana Moradora. Consumo de gás de novembro: R$ 87,40, vence em 30/11/2026", com o botão
     "Abrir no Condomix" e o rodapé de como deixar de receber.
2. **Morador (celular):**
   - O sino mostrou 1; em Notificações, a "Nova cobrança" destacada.
   - Tocar abriu Meus débitos e zerou o sino.
   - Em Conta, as preferências apareceram sem o grupo da assinatura. Desmarquei o e-mail de Documentos, e a escolha
     ficou gravada.
   - O push explicou que não liga no ambiente de desenvolvimento.
3. **Síndico (computador):**
   - O sino aparece ao lado do nome do condomínio, e o grupo "Assinatura do Condomix" aparece nas preferências.
   - Em Moradores, cadastrei um inquilino com convite: a tela disse "Convite enviado por e-mail", e o e-mail "Seu acesso
     ao Condomix: Residencial Exemplo" chegou ao Mailpit.

## Pendências
- **Push num aparelho de verdade:** a criptografia e o envio estão testados com um aparelho e um serviço de push de
  mentira (o teste decifra a mensagem como o navegador). Falta conferir no celular, com o app publicado em https.
- **Notificações para o administrador da plataforma** (fatura com problema, pagamento em duplicidade).
- **Resumo por e-mail** (um e-mail por dia em vez de vários) e **horário de silêncio** para o push.
- **WhatsApp** como canal: etapa 20.
- **RabbitMQ** quando houver mais de um servidor; até lá, a entrega e os lembretes supõem uma instância só.
