// Package marketplace — каталог one-click шаблонов приложений.
//
// Шаблон описывает всё необходимое для развёртывания: откуда взять код, как
// собрать, как запустить, какие env-переменные спросить у пользователя.
//
// В фазе 1 каталог встроен в бинарник; в фазе 3+ будет тянуться из cloud.vibefly.dev
// с поддержкой версионирования и пользовательских приватных шаблонов.
package marketplace

// Category — группировка в UI.
type Category string

const (
	CategoryProductivity Category = "productivity"
	CategoryAutomation   Category = "automation"
	CategoryPrivacy      Category = "privacy"
	CategoryDevTools     Category = "devtools"
	CategoryBots         Category = "bots"
	CategoryMonitoring   Category = "monitoring"
	CategoryDatabase     Category = "database"
	CategoryContent      Category = "content"
)

// EnvField — вопрос к пользователю на этапе install (обязательные env-переменные).
type EnvField struct {
	Key         string `json:"key"`
	Label       string `json:"label"`
	Hint        string `json:"hint,omitempty"`
	Secret      bool   `json:"secret,omitempty"`
	Default     string `json:"default,omitempty"`
	Required    bool   `json:"required"`
	Placeholder string `json:"placeholder,omitempty"`
}

// Template — один элемент каталога.
type Template struct {
	ID          string     `json:"id"`
	Name        string     `json:"name"`
	Category    Category   `json:"category"`
	Description string     `json:"description"`
	Icon        string     `json:"icon"` // emoji или идентификатор
	Homepage    string     `json:"homepage,omitempty"`
	Repo        string     `json:"repo,omitempty"`
	Image       string     `json:"image,omitempty"` // docker image (если разворачивается в контейнере)
	StartCmd    string     `json:"start_cmd"`
	DefaultPort int        `json:"default_port,omitempty"`
	MemoryMax   string     `json:"memory_max,omitempty"`
	EnvSchema   []EnvField `json:"env_schema,omitempty"`
	Tags        []string   `json:"tags,omitempty"`
}

// Catalog — каталог шаблонов.
type Catalog struct {
	items map[string]Template
}

// New возвращает встроенный каталог v0.1.
func New() *Catalog {
	items := make(map[string]Template, len(BuiltinTemplates))
	for _, t := range BuiltinTemplates {
		items[t.ID] = t
	}
	return &Catalog{items: items}
}

// List — все шаблоны в фиксированном порядке BuiltinTemplates.
func (c *Catalog) List() []Template {
	out := make([]Template, 0, len(BuiltinTemplates))
	for _, t := range BuiltinTemplates {
		if v, ok := c.items[t.ID]; ok {
			out = append(out, v)
		}
	}
	return out
}

// Get возвращает шаблон по ID.
func (c *Catalog) Get(id string) (Template, bool) {
	t, ok := c.items[id]
	return t, ok
}

// BuiltinTemplates — базовый набор v0.1.
//
// Выбирал те же концепции что в r/selfhosted и awesome-selfhosted: быстрое
// развёртывание, лёгкие приложения, реальная польза пользователю.
var BuiltinTemplates = []Template{
	{
		ID:          "vaultwarden",
		Name:        "Vaultwarden",
		Category:    CategoryPrivacy,
		Icon:        "\U0001F510",
		Description: "\u041C\u0435\u043D\u0435\u0434\u0436\u0435\u0440 \u043F\u0430\u0440\u043E\u043B\u0435\u0439 \u0441 \u043F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u043E\u0439 Bitwarden API. \u041F\u043E\u043B\u043D\u043E\u0441\u0442\u044C\u044E self-hosted.",
		Homepage:    "https://github.com/dani-garcia/vaultwarden",
		Image:       "vaultwarden/server:latest",
		StartCmd:    "vaultwarden",
		DefaultPort: 8080,
		MemoryMax:   "256M",
		Tags:        []string{"password-manager", "bitwarden", "selfhosted"},
		EnvSchema: []EnvField{
			{Key: "ADMIN_TOKEN", Label: "Admin token", Secret: true, Required: true,
				Hint: "\u0414\u043B\u044F \u0434\u043E\u0441\u0442\u0443\u043F\u0430 \u043A admin panel"},
			{Key: "DOMAIN", Label: "Domain", Required: false, Placeholder: "https://vault.example.com"},
		},
	},
	{
		ID:          "n8n",
		Name:        "n8n",
		Category:    CategoryAutomation,
		Icon:        "\U0001F517",
		Description: "Visual workflow automation. \u0410\u043B\u044C\u0442\u0435\u0440\u043D\u0430\u0442\u0438\u0432\u0430 Zapier \u0431\u0435\u0437 \u043B\u0438\u043C\u0438\u0442\u043E\u0432.",
		Homepage:    "https://n8n.io",
		Image:       "n8nio/n8n:latest",
		StartCmd:    "n8n start",
		DefaultPort: 5678,
		MemoryMax:   "512M",
		Tags:        []string{"automation", "workflow", "no-code"},
		EnvSchema: []EnvField{
			{Key: "N8N_BASIC_AUTH_USER", Label: "Admin user", Required: true, Default: "admin"},
			{Key: "N8N_BASIC_AUTH_PASSWORD", Label: "Admin password", Secret: true, Required: true},
		},
	},
	{
		ID:          "uptime-kuma",
		Name:        "Uptime Kuma",
		Category:    CategoryMonitoring,
		Icon:        "\U0001F4E1",
		Description: "\u041A\u0440\u0430\u0441\u0438\u0432\u044B\u0439 \u043C\u043E\u043D\u0438\u0442\u043E\u0440\u0438\u043D\u0433 uptime \u0438 \u0441\u0442\u0430\u0442\u0443\u0441-\u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0430 \u0432 \u043E\u0434\u043D\u043E\u043C \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0438.",
		Homepage:    "https://github.com/louislam/uptime-kuma",
		Image:       "louislam/uptime-kuma:1",
		StartCmd:    "node server/server.js",
		DefaultPort: 3001,
		MemoryMax:   "256M",
		Tags:        []string{"monitoring", "uptime", "status-page"},
	},
	{
		ID:          "pihole",
		Name:        "Pi-hole",
		Category:    CategoryPrivacy,
		Icon:        "\U0001F6E1",
		Description: "DNS sinkhole. \u0411\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u043A\u0430 \u0440\u0435\u043A\u043B\u0430\u043C\u044B \u0438 \u0442\u0440\u0435\u043A\u0435\u0440\u043E\u0432 \u043D\u0430 \u0443\u0440\u043E\u0432\u043D\u0435 \u0441\u0435\u0442\u0438.",
		Homepage:    "https://pi-hole.net",
		Image:       "pihole/pihole:latest",
		StartCmd:    "pihole-FTL",
		DefaultPort: 80,
		MemoryMax:   "256M",
		Tags:        []string{"dns", "adblock", "network"},
		EnvSchema: []EnvField{
			{Key: "WEBPASSWORD", Label: "Web password", Secret: true, Required: true},
			{Key: "TZ", Label: "Timezone", Default: "Europe/Minsk"},
		},
	},
	{
		ID:          "memos",
		Name:        "Memos",
		Category:    CategoryProductivity,
		Icon:        "\U0001F4DD",
		Description: "Lightweight note-taking + microblog. \u0410\u043B\u044C\u0442\u0435\u0440\u043D\u0430\u0442\u0438\u0432\u0430 Twitter-thread \u0434\u043B\u044F \u0441\u0435\u0431\u044F.",
		Homepage:    "https://github.com/usememos/memos",
		Image:       "neosmemo/memos:stable",
		StartCmd:    "./memos",
		DefaultPort: 5230,
		MemoryMax:   "128M",
		Tags:        []string{"notes", "microblog", "productivity"},
	},
	{
		ID:          "postgres-pgadmin",
		Name:        "PostgreSQL + pgAdmin",
		Category:    CategoryDatabase,
		Icon:        "\U0001F418",
		Description: "PostgreSQL 16 + web UI. \u0411\u044B\u0441\u0442\u0440\u044B\u0439 \u0441\u043F\u043E\u0441\u043E\u0431 \u043F\u043E\u043B\u0443\u0447\u0438\u0442\u044C \u0440\u0430\u0431\u043E\u0447\u0443\u044E \u0431\u0430\u0437\u0443 \u0441 \u0430\u0434\u043C\u0438\u043D\u043A\u043E\u0439.",
		Homepage:    "https://www.postgresql.org",
		Image:       "postgres:16-alpine",
		StartCmd:    "docker-entrypoint.sh postgres",
		DefaultPort: 5432,
		MemoryMax:   "512M",
		Tags:        []string{"database", "postgres", "sql"},
		EnvSchema: []EnvField{
			{Key: "POSTGRES_USER", Label: "Username", Default: "vibefly", Required: true},
			{Key: "POSTGRES_PASSWORD", Label: "Password", Secret: true, Required: true},
			{Key: "POSTGRES_DB", Label: "Database name", Default: "vibefly", Required: true},
		},
	},
	{
		ID:          "redis",
		Name:        "Redis 7",
		Category:    CategoryDatabase,
		Icon:        "\u26A1",
		Description: "In-memory key-value store. \u041A\u044D\u0448\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u0435, \u043E\u0447\u0435\u0440\u0435\u0434\u0438, pub/sub.",
		Homepage:    "https://redis.io",
		Image:       "redis:7-alpine",
		StartCmd:    "redis-server --save 60 1 --loglevel warning",
		DefaultPort: 6379,
		MemoryMax:   "128M",
		Tags:        []string{"cache", "redis", "queue"},
	},
	{
		ID:          "telegram-bot-template",
		Name:        "Telegram bot template",
		Category:    CategoryBots,
		Icon:        "\U0001F916",
		Description: "\u0413\u043E\u0442\u043E\u0432\u044B\u0439 \u0441\u043A\u0435\u043B\u0435\u0442 Telegram-\u0431\u043E\u0442\u0430 \u043D\u0430 grammy + TypeScript \u0441 webhook \u0438 health-check.",
		Repo:        "antsincgame/vibefly-telegram-template",
		StartCmd:    "node dist/index.js",
		DefaultPort: 4040,
		MemoryMax:   "128M",
		Tags:        []string{"telegram", "bot", "template", "nodejs"},
		EnvSchema: []EnvField{
			{Key: "BOT_TOKEN", Label: "Bot token", Secret: true, Required: true,
				Hint: "\u041E\u0442 @BotFather"},
			{Key: "WEBHOOK_DOMAIN", Label: "Webhook domain", Placeholder: "https://bot.example.com"},
		},
	},
	{
		ID:          "astro-blog",
		Name:        "Astro blog",
		Category:    CategoryContent,
		Icon:        "\u2728",
		Description: "\u0421\u0442\u0430\u0442\u0438\u0447\u043D\u044B\u0439 \u0431\u043B\u043E\u0433 \u043D\u0430 Astro \u0441 \u0432\u0441\u0442\u0440\u043E\u0435\u043D\u043D\u044B\u043C\u0438 RSS \u0438 sitemap. \u041F\u0438\u0448\u0435\u0448\u044C markdown \u2014 \u043F\u043E\u043B\u0443\u0447\u0430\u0435\u0448\u044C \u0441\u0430\u0439\u0442.",
		Repo:        "antsincgame/vibefly-astro-template",
		StartCmd:    "npx --yes astro dev --host 0.0.0.0",
		DefaultPort: 4321,
		MemoryMax:   "256M",
		Tags:        []string{"blog", "static", "astro"},
	},
	{
		ID:          "code-server",
		Name:        "code-server",
		Category:    CategoryDevTools,
		Icon:        "\U0001F4BB",
		Description: "VS Code \u0432 \u0431\u0440\u0430\u0443\u0437\u0435\u0440\u0435. \u041A\u043E\u0434\u0438\u0442\u044C \u0441 \u043F\u043B\u0430\u043D\u0448\u0435\u0442\u0430 \u0438\u043B\u0438 \u0447\u0443\u0436\u043E\u0433\u043E \u043B\u044D\u043F\u0442\u043E\u043F\u0430.",
		Homepage:    "https://github.com/coder/code-server",
		Image:       "codercom/code-server:latest",
		StartCmd:    "code-server --bind-addr 0.0.0.0:8080",
		DefaultPort: 8080,
		MemoryMax:   "1G",
		Tags:        []string{"ide", "vscode", "remote-dev"},
		EnvSchema: []EnvField{
			{Key: "PASSWORD", Label: "Access password", Secret: true, Required: true},
		},
	},
}
