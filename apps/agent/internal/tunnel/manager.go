// Package tunnel — управление публичным ingress (Cloudflare Tunnel).
//
// Зачем: телефон-сервер сидит за NAT мобильного оператора, наружу его иначе
// не достать. Cloudflare Tunnel пробрасывает локальный порт агента (3001 или
// порты пользовательских приложений) в публичный URL без открытия портов на
// устройстве и без сложной настройки.
//
// Сейчас реализован quick tunnel (TryCloudflare): cloudflared --url создаёт
// одноразовый поддомен *.trycloudflare.com без auth и без домена. Этого
// достаточно для dogfood. Named tunnels с собственным доменом (например,
// my-server.vibefly.app) — следующая итерация.
package tunnel

import (
	"context"
	"errors"
	"time"
)

// Provider — какой тип туннеля используется.
type Provider string

const (
	ProviderNone          Provider = "none"
	ProviderTryCloudflare Provider = "trycloudflare"
	ProviderNamedTunnel   Provider = "named"
)

// Status — снимок текущего состояния туннеля. Сериализуется в JSON для UI.
//
// StartedAt — указатель чтобы omitempty работал для нулевого значения.
type Status struct {
	Active    bool       `json:"active"`
	PublicURL string     `json:"public_url,omitempty"`
	StartedAt *time.Time `json:"started_at,omitempty"`
	Provider  Provider   `json:"provider"`
	LastError string     `json:"last_error,omitempty"`
}

// Event — изменение статуса. Слушатели получают через Subscribe.
type Event struct {
	Status Status `json:"status"`
}

// Manager — обёртка над инструментом туннелирования.
//
// Start/Stop идемпотентны: повторный Start активного туннеля возвращает
// ErrAlreadyActive, повторный Stop неактивного — nil.
type Manager interface {
	Start(ctx context.Context) (Status, error)
	Stop() error
	Status() Status
	Subscribe() <-chan Event
	Close() error
}

// ErrAlreadyActive — попытка запустить уже работающий туннель.
var ErrAlreadyActive = errors.New("tunnel: already active")

// ErrNotConfigured — туннель не настроен (нет cloudflared, отключён в конфиге).
var ErrNotConfigured = errors.New("tunnel: not configured")

// Nop — заглушка Manager для систем без cloudflared (macOS dev, CI).
// Все методы возвращают ErrNotConfigured/пустой статус и не делают сетевых вызовов.
type Nop struct{}

func (Nop) Start(_ context.Context) (Status, error) {
	return Status{Provider: ProviderNone, LastError: "cloudflared not configured"}, ErrNotConfigured
}
func (Nop) Stop() error    { return nil }
func (Nop) Status() Status { return Status{Provider: ProviderNone} }
func (Nop) Subscribe() <-chan Event {
	ch := make(chan Event)
	close(ch)
	return ch
}
func (Nop) Close() error { return nil }
