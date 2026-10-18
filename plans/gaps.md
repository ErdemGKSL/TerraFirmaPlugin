# Eksik işler (unutulmaması gerekenler)

Her madde kullanıcı tarafından istendi ya da bu oturumda doğrulamada ortaya çıktı. Kapanınca işaretlenir.

- [x] **Mevsimsel çimen rengi, sonbahar tonu.** Çimen rengi geç sonbaharda turuncu-sarımsı olmalı (TFC'de iklime göre sürekli değişir). Şu an mevsim başına tek renk örneklenir (ledger satır 103), bu yüzden Ekim–Kasım arası aynı renk. Düzeltme: renk örneklemesini ayda bir (ya da daha sık) yapmak; M2-SEA-01/02 veri yapısı ve yeniden gönderme etkilenir. Karar: ledger sapmasını kaldır ve uygula. Yapıldı: renk evresi artık takvim ayı (12 evre; ayın orta günü öğlen örneklenir), ay değişince parçalar aynı `ResendLimiter` ile yeniden gönderilir. Evre yeni biyom eklemez (varyant bir renk bandıdır, evre yalnızca bandı seçer), veri paketi 2.000 biyom / 8,4 MB olarak kaldı ve yeniden üretimde değişmedi. Kuru sonbaharlı bir parça Ekim'de `t1r2` (zeytin yeşili), Kasım'da `t1r3` (turuncu-sarı, `#fbd459`); Şubat ile Mart da farklı (`SeasonalBiomeRemapTest`). Sunucudaki görsel doğrulama aşağıdaki maddede açık.
- [ ] **Mevsimsel çimen rengi, sunucu kontrolü.** Yaz ve kış tonunun farkı, kar örtüsü olmayan bir test yüzeyinde ölçülmeli (M2-SEA-02).
- [ ] **Renk ölçümünde kar örtüsü.** Test yüzeyi kar yağmayan bir noktada ya da kapalı bir yapıda kurulmalı (test altyapısı).
- [ ] **Headless istemci ve CraftEngine eşlemesi.** Vanilla `grass_block` ve `tfp:grass` istemcide toprak görünümüne eşleniyordu; düzeltildi (1e86e53), ama bu düzeltmenin sunucuda mevsim farkıyla doğrulanması açık.
- [ ] **M2-GEN-03 görsel harita eşleşmesi.** Render edilmiş bölge haritalarının TFC'nin haritalarıyla görsel eşleşmesi.
- [ ] **M2-GEN-04 sunucu kontrolü ve nehir haritası.** Göl ve nehir oyma sunucuda doğrulanmalı; nehir haritası render edilmeli.
- [x] **M2-GEN-06 blok yoğunluğu.** Sarkıntılar, kemerler, deniz kayaları portlandı (golden testler TFC'den). Açık kalanlar: caves/beardifier (M2-GEN-09) ve kemer altındaki zeminin malzemesi.
- [ ] **M2-GEN-07 üretim testi.** Dünyaların üretildiğini ve CraftEngine'in blokları izlediğini kanıtlayan resmi test (self-test şu an bunu doğrudan doğrulamıyor).
- [ ] **M2-GEN-11, GEN-15, GEN-16.** Plan maddelerine göre kısmi/açık işler.
- [ ] **M2-SEA-02 kalan.** Snow/sea-ice ile ilgili kalan parçalar ve CraftEngine blokları.
- [ ] **M2-WTH-03 kalan.** Deniz buzu gerçek iklimle; CraftEngine kar yığını blokları.
- [ ] **Kurulum yeniden başlatma akışı.** İlk başlatma düzeltildi (5ded3fe); doğrulandı.
- [ ] **M3–M6 ve çapraz konular.** Hiç başlanmadı.

## Sunucu kontrolü: sonbahar tonu (açık, bulgu)

- [ ] (Kök neden bulundu: iki test sitesi yüksek dağ sütunlarıydı, y ≥ 173; TFC'deki yükseklik soğutması orada -17.8 °C ekliyor, yani kar beklenen sonuç. İklim kodu TFC ile satır satır eşleşiyor. Sonraki adım: y ≈ 64–80 civarında alçak bir site seçip `tfp debug chunk` ile ortalama 2–5 °C'yi doğrulamak.) Sonbahar çimen tonu sunucuda ölçülemedi. Denenen sitelerde (0,0; -220,-1200) Ekim–Kasımda, hatta Eylül'de bile kar çimenin üstünde. Test dünyası genel olarak çok soğuk görünüyor (Eylül'de -14 °C, ortalama -15.8 °C); bu, iklim modelinin ya da kar kuralının fazla soğuk veya fazla agresif olabileceğini gösteriyor. Yapılacak: iklim değerlerinin TFC'deki ortalamalarla karşılaştırılması ve yıllık ortalamanın makul olup olmadığının kontrolü; ardından kar örtüsü olmayan bir sitede renk ölçümü.
