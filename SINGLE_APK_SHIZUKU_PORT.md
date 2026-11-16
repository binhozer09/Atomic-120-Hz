# Atomic 120 Hz — integração cliente Shizuku

## Fluxo do usuário
1. Instalar o Shizuku separadamente pelo botão dentro do Atomic.
2. Abrir o Shizuku e iniciar o serviço usando Depuração sem fio do Android.
3. Voltar ao Atomic e tocar em **Conectar ao Shizuku / Autorizar**.
4. Aceitar a solicitação de permissão exibida pelo Shizuku.
5. Tocar em **Executar comandos para 90 FPS**.

## Implementação
- Dependências oficiais `dev.rikka.shizuku:api:13.1.5` e `provider:13.1.5`.
- Provider oficial declarado no Manifest.
- A autorização é solicitada pela API Shizuku; o usuário precisa concedê-la.
- Os comandos são executados como processo shell via Shizuku e o código de saída é verificado.
- O botão de download abre a página do Shizuku na Play Store; se indisponível, abre a página oficial de download.
- O app não tenta iniciar Shizuku silenciosamente: em aparelhos sem root, o usuário precisa iniciar o serviço pelo próprio Shizuku após configurar a Depuração sem fio.

## Limitações
- A permissão ADB/Shell não equivale a root e varia por versão do Android e fabricante.
- Ajustar `min_refresh_rate` e `peak_refresh_rate` não garante que um jogo renderize 90 FPS. O jogo e o firmware podem impor limites.
- O status do Atomic só indica autorização e execução confirmada dos comandos, não FPS real do jogo.
