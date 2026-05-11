package metrics

import (
	"testing"
	"time"
)

func TestSyntheticReader(t *testing.T) {
	r := syntheticReader{}
	s := r.Read()

	if s.BatteryLevel < 0 || s.BatteryLevel > 100 {
		t.Errorf("невалидный уровень батареи: %d", s.BatteryLevel)
	}
	if s.RAMTotalMB <= 0 {
		t.Errorf("RAM total должен быть положительным, получен %d", s.RAMTotalMB)
	}
	if time.Since(s.Timestamp) > time.Second {
		t.Errorf("timestamp слишком старый: %s", s.Timestamp)
	}
}

func TestNew(t *testing.T) {
	r := New()
	if r == nil {
		t.Fatal("New() вернул nil")
	}
	s := r.Read()
	if s.Timestamp.IsZero() {
		t.Error("timestamp не заполнен")
	}
}
