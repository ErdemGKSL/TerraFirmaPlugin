# Eksik işler (unutulmaması gerekenler)

Her madde kullanıcı tarafından istendi ya da bu oturumda doğrulamada ortaya çıktı. Kapanınca işaretlenir.

- [x] **Mevsimsel çimen rengi, sonbahar tonu.** Çimen rengi geç sonbaharda turuncu-sarımsı olmalı (TFC'de iklime göre sürekli değişir). Şu an mevsim başına tek renk örneklenir (ledger satır 103), bu yüzden Ekim–Kasım arası aynı renk. Düzeltme: renk örneklemesini ayda bir (ya da daha sık) yapmak; M2-SEA-01/02 veri yapısı ve yeniden gönderme etkilenir. Karar: ledger sapmasını kaldır ve uygula. Yapıldı: renk evresi artık takvim ayı (12 evre; ayın orta günü öğlen örneklenir), ay değişince parçalar aynı `ResendLimiter` ile yeniden gönderilir. Evre yeni biyom eklemez (varyant bir renk bandıdır, evre yalnızca bandı seçer), veri paketi 2.000 biyom / 8,4 MB olarak kaldı ve yeniden üretimde değişmedi. Kuru sonbaharlı bir parça Ekim'de `t1r2` (zeytin yeşili), Kasım'da `t1r3` (turuncu-sarı, `#fbd459`); Şubat ile Mart da farklı (`SeasonalBiomeRemapTest`). Sunucudaki görsel doğrulama aşağıdaki maddede açık.
- [ ] **Mevsimsel çimen rengi, sunucu kontrolü.** Yaz ve kış tonunun farkı, kar örtüsü olmayan bir test yüzeyinde ölçülmeli (M2-SEA-02). Sonbahar: yağış bantları artık eşit değil, renk haritasının değiştiği yere göre (250 / 150 / 75 mm kenarları); kuru soğukta (-6 C, 130 mm) zeytin-sarı bant seçiliyor. Ele alındı; oyun içi kontrol bekliyor.
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

## Sonbahar tonu: alçak sitede sonuç (kısmi)

- [x] Kar sorunu çözüldü: alçak site (chunk 0,400; yüzey y 63; ortalama 5.2 °C) kışın bile kar örtmüyor.
- [ ] Renk ölçümü temiz değil: Eylül'de yağmur (RAIN) ekranı mavileştirdi; R−B değerleri Eylül +0.8, Ekim −11.5, Kasım −11.8. Kasım turuncu görünmüyor, ama yağmur katmanı karşılaştırmayı bozuyor. Sonraki adım: vanilla `weather clear` ile yağmuru kapatıp üç ayı aynı koşulda yeniden ölçmek.

## Sonbahar tonu: temiz hava ölçümü (sonuç: yeterli değil)

- [ ] Yağmursuz ölçüm (alçak site 0,400): Eylül yeşil-zeytin (R−B 25), Ekim ve Kasım birebir aynı koyu yeşil (R−B 6). Kasım turuncu değil. Muhtemel neden: site ıslak (yağış 236–248 mm); kodda turuncu tonu yalnızca kuru sonbaharda çıkıyor (birim testinde 150 mm). Sonraki adım: yağış değeri düşük (örneğin 150 mm altı) bir alçak site bulup aynı üç ayı ölçmek.

## Sonbahar tonu: kuru site bulunamadı (sonuç: belirsiz)

- [ ] Yağış 150 mm altı alçak site bulunamadı; (-220,0) sitesinde yağış aylara göre değişiyor (Eylül 254, Ekim 235, Kasım 215 mm). Üç ayda da renk aynı (Ekim = Kasım, R−B 6.3). Sonuç: bu test dünyasında sonbahar turuncusu gözlemlenemedi. Olası nedenler: (a) bu dünyada gerçekten kuru alçak site yok (iklim/yağış dağılımı kontrol edilmeli, TFC yağış ortalamalarıyla karşılaştırılmalı); (b) kuru sonbahar koşulu (yağış düşüklüğü) sunucuda değişen aylık yağış ile eşleşmiyor. Sonraki adım: yağış dağılımını TFC'nin bölgesel yağış ortalamalarıyla karşılaştırmak.

- [x] Yağış modeli TFC ile satır satır eşleşiyor (formüller, bölge gürültüsü, sınırlar 0–500 mm, kıyı etkisi). Sorun model değil; test sitelerinin spawn'a yakın (x ≈ 0 ile -380 arası) olması. TFC'de 150 mm altı yağış ancak x ≈ -3 ile -4 km batıda görülüyor. Sonraki adım: x ≈ -4000 civarında bir alçak site, yağışı 150 mm altında doğrulayıp sonbahar ölçümünü orada yapmak.

- [ ] Batı sitesi (x -7000, yağış 98–130 mm, ortalama 4.3 °C): Eylül ve Ekim kahverengi-bej, Kasım koyu yeşil (R−B +32 → +27 → −10). Kasım turuncu değil. Ekrandaki yağmur çizgileri "weather clear" sonrasında da kalıyor, yani renk ölçümü hâlâ kirli. Sonraki adım: yağmur katmanının kaynağını bulmak (vanilla hava durumu temizlenmiyor mu, yoksa plugin'in yağmur görseli mi) ve sonra ölçümü tekrarlamak.
- Yağmur kaynağı için hipotez: `weather clear 600` yalnızca 600 tick (30 s) temiz tutar; testte bekleme 40 s olduğu için vanilla hava 30 s sonra yağmura dönmüş olabilir. Plugin kodunda yağmur çizen bir şey bulunmadı. Doğrulama: `gamerule doWeatherCycle false` ile hava döngüsünü kapatıp `weather clear` yapmak ve ölçümü tekrarlamak.

- [x] Yağmur hipotezi doğrulandı: hava döngüsü kapalıyken (`advance_weather false`) yağmur çizgisi yok; ölçüm temiz.
- [ ] Temiz ölçüm sonucu (batı sitesi, yağış 100–130 mm, ortalama 4.3 °C): Eylül altın-zeytin (R−B 91), Ekim zeytin-sarı (R−B 79), Kasım koyu yeşil (R−B 6, sıcaklık −6 °C). Yani kuru sonbaharda turuncu değil, Kasım'da yeşile dönüyor. Bu, renk modelinin sıcaklıkla yeşile kaydığını ve geç sonbahar tonunun TFC ile uyuşmadığını gösteriyor olabilir. Sonraki adım: TFC'nin grass colormap indeksini (sıcaklık/yağış) bizim eşlememizle karşılaştırmak; hangi sıcaklıkta hangi renge gittiğini doğrulamak.

- [x] Yeni bantların sunucu kontrolü (batı sitesi, hava kapalı): Kasım yağış 118 mm, −1.6 °C, renk zeytin-yeşil/zeytin-sarı (R−B 46, önceki koyu yeşil 6'ya karşı). Eylül altın-zeytin (R−B 81). Fark belirgin ama Kasım'ın turuncu-sarı marjı dar; önceki koyu yeşil ölçümü bu koşulda tekrarlanmadı. Kalan: Kasım'ın oyun içi görsel onayı ve 150–186 mm arası yeşil bantlar.

- [~] M2-GEN-16 spawn ve dünya ayarları: kod bağlı ve test edilmiş (`TfcChunkGenerator` spawn araması, `TfcSpawnTest`; yapılar kapalı, `shouldGenerateStructures` false). Taze dünya kontrolü: bot kuru karada (taş), sıcaklık 10.2 °C, yağış 258 mm, hata yok; ama bot (1147.5, 237.5) seviyesi spawn'ı (1149, 235) 2 blok uzakta. Neden bulunamadı; oyuncunun hareketi ya da kayıt farkı olabilir. Açık: bu 2 blok farkının nedenini doğrulamak ve TFC spawn aramasının log satırı eklemek.
