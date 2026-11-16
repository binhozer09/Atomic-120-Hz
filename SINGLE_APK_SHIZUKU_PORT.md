# Atomic 120 Hz — plano de integração Shizuku em APK único

## Objetivo
Entregar um único APK instalável chamado Atomic 120 Hz, sem exigir que o usuário instale separadamente o aplicativo Shizuku.

## Restrição técnica confirmada
A dependência oficial `dev.rikka.shizuku:api` + `provider` é apenas uma API cliente. A documentação oficial exige que Shizuku ou Sui já esteja instalado e que o serviço Shizuku seja iniciado. Portanto, adicionar essas dependências ao Atomic NÃO atende ao requisito de APK único.

## Arquitetura necessária
Para um único APK, será necessário portar/adaptar componentes do projeto oficial Shizuku (licença Apache-2.0) para o próprio aplicativo Atomic:
1. Reutilizar o mecanismo de inicialização do servidor Shizuku com identidade shell (UID 2000), preservando o fluxo oficial de pareamento/Depuração sem fio do Android.
2. Adaptar o gerenciador/fluxo de inicialização para a interface Atomic, sem copiar ícones ou identidade visual do Shizuku e sem usar o nome ou applicationId reservados do Shizuku.
3. Empacotar os binários/recursos do servidor compatíveis com ABI e versão Android, com verificação de integridade.
4. Manter a exigência de confirmação do sistema Android para Depuração sem fio; não prometer pareamento silencioso nem inicialização privilegiada sem autorização.
5. Criar uma camada de serviço interna para executar comandos permitidos, coletar stdout/stderr e exit code e registrar logs.
6. Validar a conexão com uma operação real e UID shell (2000) antes de mostrar CONECTADO.
7. Só então habilitar o painel de ajustes de frequência. Não afirmar que o jogo está renderizando a 90/120 FPS sem medição independente.

## Estado atual
- A branch `shizuku-integrado` foi criada a partir do commit que gerou o último APK testável.
- O APK anterior permanece na branch `main`.
- Nenhuma integração cliente via Shizuku API será tratada como solução final do requisito de APK único.
- A implementação precisa ser feita e compilada por etapas; este documento não declara que a integração já está pronta.

## Licença e identidade
O projeto oficial Shizuku é Apache-2.0. A redistribuição/adaptação deve preservar avisos/licença aplicáveis. Não reutilizar os ícones launcher do Shizuku, não chamar o app de Shizuku e não usar `moe.shizuku.privileged.api` como applicationId. O produto e a interface final permanecem Atomic 120 Hz.

## Referências
- https://github.com/RikkaApps/Shizuku
- https://github.com/RikkaApps/Shizuku-API
- https://shizuku.rikka.app/
