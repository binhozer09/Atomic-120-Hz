const MODEL = "gpt-6-astra";
const WINDOW_MS = 60_000;
const MAX_REQUESTS_PER_WINDOW = 12;
const requests = new Map();

const schema = {
  type: "object",
  additionalProperties: false,
  properties: {
    summary: { type: "string" },
    current_refresh_rate_hz: { type: "string" },
    max_supported_refresh_rate_hz: { type: "string" },
    likely_limiters: { type: "array", items: { type: "string" } },
    steps: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          title: { type: "string" },
          instructions: { type: "string" }
        },
        required: ["title", "instructions"]
      }
    },
    commands: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          command: { type: "string" },
          purpose: { type: "string" },
          requires_adb: { type: "boolean" },
          risk: { type: "string", enum: ["baixo", "moderado", "alto"] }
        },
        required: ["command", "purpose", "requires_adb", "risk"]
      }
    },
    limitation: { type: "string" }
  },
  required: [
    "summary",
    "current_refresh_rate_hz",
    "max_supported_refresh_rate_hz",
    "likely_limiters",
    "steps",
    "commands",
    "limitation"
  ]
};

function rateLimit(req) {
  const forwarded = req.headers["x-forwarded-for"];
  const ip = (Array.isArray(forwarded) ? forwarded[0] : forwarded || "unknown").split(",")[0].trim();
  const now = Date.now();
  const entry = requests.get(ip);
  if (!entry || now - entry.start >= WINDOW_MS) {
    requests.set(ip, { start: now, count: 1 });
    return true;
  }
  if (entry.count >= MAX_REQUESTS_PER_WINDOW) return false;
  entry.count += 1;
  return true;
}

function allowedCommand(command) {
  if (typeof command !== "string" || command.length > 300) return false;
  if (/[;|&`\n\r]|\$\(|\bsu\b|\brm\b|\breboot\b|\bpm\s+(disable|uninstall)\b|settings\s+delete/i.test(command)) return false;
  const normalized = command.trim().replace(/^adb\s+shell\s+/, "");
  return [
    /^settings get (system|secure) [a-z0-9_]+$/,
    /^settings put (system|secure) (peak_refresh_rate|min_refresh_rate|user_refresh_rate|miui_refresh_rate) (60|90|120)(\.0)?$/,
    /^cmd display get-user-preferred-display-mode(?: 0)?$/,
    /^cmd display set-user-preferred-display-mode \d+ \d+ (60|90|120)(\.0)? 0$/,
    /^device_config get game_overlay com\.dts\.freefireth$/,
    /^device_config put game_overlay com\.dts\.freefireth mode=(2|3),fps=(60|90|120)(:mode=(2|3),fps=(60|90|120))?$/,
    /^dumpsys (display|game)$/
  ].some((pattern) => pattern.test(normalized));
}

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Use POST." });
  if (!rateLimit(req)) return res.status(429).json({ error: "Muitas análises em pouco tempo. Aguarde um minuto." });

  if (!process.env.OPENAI_API_KEY) {
    return res.status(503).json({ error: "A API ainda não foi configurada no servidor. Falta cadastrar OPENAI_API_KEY nas variáveis do projeto." });
  }

  let device;
  try {
    device = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
  } catch {
    return res.status(400).json({ error: "JSON inválido." });
  }
  if (!device || typeof device !== "object" || JSON.stringify(device).length > 12_000) {
    return res.status(400).json({ error: "Dados do aparelho ausentes ou muito grandes." });
  }

  const instructions = [
    "Você é o motor de diagnóstico do aplicativo Android Atomic 120 Hz.",
    "Analise somente os dados fornecidos. Não invente medições, permissões, resultados de comandos ou capacidades do aparelho.",
    "O objetivo é investigar por que Free Fire pode ficar em 60 FPS apesar de a tela suportar 90 Hz.",
    "Diferencie taxa de atualização do painel (Hz) de FPS renderizado pelo jogo.",
    "Nunca prometa nem afirme que um comando garante 90 FPS. O jogo, o firmware HyperOS, a política térmica e a lista de dispositivos suportados podem limitar o FPS.",
    "O aplicativo tem integração com Shizuku. Prepare comandos shell seguros para o Atomic executar somente depois de o usuário iniciar o Shizuku, autorizar o Atomic e tocar no botão de execução. Nunca diga que já foram executados.",
    "Sugira primeiro verificações seguras na interface do sistema e nas configurações do jogo.",
    "Se incluir comandos, use apenas comandos Android/ADB de leitura ou ajustes de refresh-rate e game_overlay do pacote com.dts.freefireth. Não sugira root, exploits, desativação de segurança, remoção de pacotes, alteração de partições, comandos destrutivos ou mudanças irreversíveis.",
    "Use no máximo 5 comandos, cada um em uma única linha, SEM prefixo adb shell. O Atomic executa o comando pelo Shizuku. Não use pipes, ponto e vírgula, encadeamento ou scripts.",
    "Marque requires_adb=true para comandos shell que serão enviados ao Shizuku. Use risco moderado quando alterar configurações.",
    "Se não houver base suficiente, diga exatamente o que falta. Responda em português brasileiro, com instruções simples."
  ].join("\n");

  try {
    const upstream = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${process.env.OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: MODEL,
        instructions,
        input: [{ role: "user", content: [{ type: "input_text", text: JSON.stringify(device) }] }],
        text: {
          format: {
            type: "json_schema",
            name: "atomic_device_diagnosis",
            strict: true,
            schema
          }
        },
        max_output_tokens: 1800
      })
    });

    const raw = await upstream.json();
    if (!upstream.ok) {
      console.error("OpenAI API error", upstream.status, raw?.error?.code || raw?.error?.type || "unknown");
      return res.status(502).json({ error: "A análise de IA falhou. Verifique a configuração e os créditos da API." });
    }

    const outputText = (raw.output || [])
      .filter((item) => item.type === "message")
      .flatMap((item) => item.content || [])
      .find((part) => part.type === "output_text")?.text;

    if (!outputText) return res.status(502).json({ error: "A IA não retornou um diagnóstico completo." });

    const analysis = JSON.parse(outputText);
    analysis.commands = (analysis.commands || [])
      .filter((item) => allowedCommand(item.command))
      .slice(0, 5);

    return res.status(200).json({ analysis, model: MODEL });
  } catch (error) {
    console.error("Atomic AI endpoint failure", error?.message || "unknown");
    return res.status(500).json({ error: "Erro interno ao processar o diagnóstico." });
  }
}
