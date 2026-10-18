# Eksik işler (unutulmaması gerekenler)

Her madde kullanıcı tarafından istendi ya da bu oturumda doğrulamada ortaya çıktı. Kapanınca işaretlenir.

- [ ] **Mevsimsel çimen rengi, sonbahar tonu.** Çimen rengi geç sonbaharda turuncu-sarımsı olmalı (TFC'de iklime göre sürekli değişir). Şu an mevsim başına tek renk örneklenir (ledger satır 103), bu yüzden Ekim–Kasım arası aynı renk. Düzeltme: renk örneklemesini ayda bir (ya da daha sık) yapmak; M2-SEA-01/02 veri yapısı ve yeniden gönderme etkilenir. Karar: ledger sapmasını kaldır ve uygula.
- [ ] **Mevsimsel çimen rengi, sunucu kontrolü.** Yaz ve kış tonunun farkı, kar örtüsü olmayan bir test yüzeyinde ölçülmeli (M2-SEA-02).
- [ ] **Renk ölçümünde kar örtüsü.** Test yüzeyi kar yağmayan bir noktada ya da kapalı bir yapıda kurulmalı (test altyapısı).
- [ ] **Headless istemci ve CraftEngine eşlemesi.** Vanilla `grass_block` ve `tfp:grass` istemcide toprak görünümüne eşleniyordu; düzeltildi (1e86e53), ama bu düzeltmenin sunucuda mevsim farkıyla doğrulanması açık.
- [ ] **M2-GEN-03 görsel harita eşleşmesi.** Render edilmiş bölge haritalarının TFC'nin haritalarıyla görsel eşleşmesi.
- [ ] **M2-GEN-04 sunucu kontrolü ve nehir haritası.** Göl ve nehir oyma sunucuda doğrulanmalı; nehir haritası render edilmeli.
- [ ] **M2-GEN-06 blok yoğunluğu.** Sarkıntılar, kemerler, deniz kayaları (agent çalışıyor; sonucu bekleniyor).
- [ ] **M2-GEN-07 üretim testi.** Dünyaların üretildiğini ve CraftEngine'in blokları izlediğini kanıtlayan resmi test (self-test şu an bunu doğrudan doğrulamıyor).
- [ ] **M2-GEN-11, GEN-15, GEN-16.** Plan maddelerine göre kısmi/açık işler.
- [ ] **M2-SEA-02 kalan.** Snow/sea-ice ile ilgili kalan parçalar ve CraftEngine blokları.
- [ ] **M2-WTH-03 kalan.** Deniz buzu gerçek iklimle; CraftEngine kar yığını blokları.
- [ ] **Kurulum yeniden başlatma akışı.** İlk başlatma düzeltildi (5ded3fe); doğrulandı.
- [ ] **M3–M6 ve çapraz konular.** Hiç başlanmadı.
