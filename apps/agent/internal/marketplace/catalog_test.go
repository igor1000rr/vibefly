package marketplace

import "testing"

func TestCatalog_List(t *testing.T) {
	c := New()
	items := c.List()
	if len(items) != len(BuiltinTemplates) {
		t.Fatalf("ожидали %d шаблонов, получили %d", len(BuiltinTemplates), len(items))
	}
}

func TestCatalog_Get(t *testing.T) {
	c := New()
	if _, ok := c.Get("vaultwarden"); !ok {
		t.Error("vaultwarden должен быть")
	}
	if _, ok := c.Get("несуществующий"); ok {
		t.Error("несуществующий не должен находиться")
	}
}

func TestBuiltinTemplates_RequiredFields(t *testing.T) {
	for _, tpl := range BuiltinTemplates {
		if tpl.ID == "" {
			t.Errorf("пустой ID в шаблоне %q", tpl.Name)
		}
		if tpl.Name == "" {
			t.Errorf("пустое Name в шаблоне %q", tpl.ID)
		}
		if tpl.StartCmd == "" {
			t.Errorf("пустой StartCmd в шаблоне %q", tpl.ID)
		}
		if tpl.Category == "" {
			t.Errorf("пустая Category в шаблоне %q", tpl.ID)
		}
		if tpl.Icon == "" {
			t.Errorf("пустой Icon в шаблоне %q", tpl.ID)
		}
	}
}
