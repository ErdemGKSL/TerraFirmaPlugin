# Eksik işler (unutulmaması gerekenler)

Her madde kullanıcı tarafından istendi ya da bu oturumda doğrulamada ortaya çıktı. Kapanınca işaretlenir.

- [x] **Mevsimsel çimen rengi, sonbahar tonu.** Çimen rengi geç sonbaharda turuncu-sarımsı olmalı (TFC'de iklime göre sürekli değişir). Şu an mevsim başına tek renk örneklenir (ledger satır 103), bu yüzden Ekim–Kasım arası aynı renk. Düzeltme: renk örneklemesini ayda bir (ya da daha sık) yapmak; M2-SEA-01/02 veri yapısı ve yeniden gönderme etkilenir. Karar: ledger sapmasını kaldır ve uygula. Yapıldı: renk evresi artık takvim ayı (12 evre; ayın orta günü öğlen örneklenir), ay değişince parçalar aynı `ResendLimiter` ile yeniden gönderilir. Evre yeni biyom eklemez (varyant bir renk bandıdır, evre yalnızca bandı seçer), veri paketi 2.000 biyom / 8,4 MB olarak kaldı ve yeniden üretimde değişmedi. Kuru sonbaharlı bir parça Ekim'de `t1r2` (zeytin yeşili), Kasım'da `t1r3` (turuncu-sarı, `#fbd459`); Şubat ile Mart da farklı (`SeasonalBiomeRemapTest`). Sunucudaki görsel doğrulama aşağıdaki maddede açık.
- [ ] **Mevsimsel çimen rengi, sunucu kontrolü.** Yaz ve kış tonunun farkı, kar örtüsü olmayan bir test yüzeyinde ölçülmeli (M2-SEA-02). Sonbahar: yağış bantları artık eşit değil, renk haritasının değiştiği yere göre (250 / 150 / 75 mm kenarları); kuru soğukta (-6 C, 130 mm) zeytin-sarı bant seçiliyor. Ele alındı; oyun içi kontrol bekliyor.
- [ ] **Renk ölçümünde kar örtüsü.** Test yüzeyi kar yağmayan bir noktada ya da kapalı bir yapıda kurulmalı (test altyapısı).
- [ ] **Headless istemci ve CraftEngine eşlemesi.** Vanilla `grass_block` ve `tfp:grass` istemcide toprak görünümüne eşleniyordu; düzeltildi (1e86e53), ama bu düzeltmenin sunucuda mevsim farkıyla doğrulanması açık.
- [ ] **M2-GEN-03 görsel harita eşleşmesi.** Render edilmiş bölge haritalarının TFC'nin haritalarıyla görsel eşleşmesi.
- [ ] **M2-GEN-04 sunucu kontrolü ve nehir haritası.** Göl ve nehir oyma sunucuda doğrulanmalı; nehir haritası render edilmeli.
- [x] **M2-GEN-06 blok yoğunluğu.** Sarkıntılar, kemerler, deniz kayaları portlandı (golden testler TFC'den). Açık kalanlar: caves/beardifier (M2-GEN-09) ve kemer altındaki zeminin malzemesi.
- [x] **M2-GEN-07 üretim testi (kanıtlandı: `worldgen.terrain_generates` self-test PASSED; 16/16, 0 hata, Paper 1.21.11).** Dünyaların üretildiğini ve CraftEngine'in blokları izlediğini kanıtlayan resmi test (self-test şu an bunu doğrudan doğrulamıyor).
- [ ] **M2-GEN-11, GEN-16.** Plan maddelerine göre kısmi/açık işler.
- [ ] **M2-GEN-15 worldgen maliyeti.** [x] Tam doldurma yolu (`TfcTerrain.fill`) sunucuda ölçüldü: `/tfp debug worldgen 300`, ortalama 26.9–29.9 ms, p95 39–41 ms (`docs/perf/worldgen-chunk-cost.md`). [ ] Vanilla taban ölçümü (aynı parçaları vanilla ve TFC dünyasında eşit iş parçacığıyla önceden üretip saniyedeki parça sayısını karşılaştırmak). [ ] Açıkta kalan mücevher (gem overlay) kararı.
- [ ] **M2-SEA-02 kalan.** Snow/sea-ice ile ilgili kalan parçalar ve CraftEngine blokları.
- [ ] **M2-WTH-03 kalan.** Deniz buzu gerçek iklimle; CraftEngine kar yığını blokları.
- [ ] **Kurulum yeniden başlatma akışı.** İlk başlatma düzeltildi (5ded3fe); doğrulandı.
- [ ] **M3–M6 ve çapraz konular.** M3–M6 kural seviyesinde başladı (plans/03–06 durum satırları); oyun içi, görsel ve glue işleri açık (aşağıdaki satırlar).

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

- [x] İlk giriş spawn doğrulaması (taze dünya): oyuncu (-3440, 69, 2080) = level.dat spawn (-3440, 69, 2080), x/y/z birebir eşleşti; hata yok.
- [~] Spawn eşitleme (eski kayıt): bu turda level.dat spawn'ı ayarlanan değerle eşleşti (1974, 65, 1311) ve loglar çağrıyı gösteriyor; önceki FAIL (2320,65,-1716 vs 2327,88,-1726) açıklanmadı. Oyuncu ölçümü: level.dat spawn'ı (-2304, 93, 1952) doğru; oyuncu (-2299, 93, 1945) konumunda, 5 ve -7 blok fark. Muhtemel neden: vanilla'nın spawn_radius kuralı (varsayılan 10 blok) ilk girişte oyuncuyu dünya spawn'ının etrafında rastgele bir noktaya koyar. Doğrulama denendi: `spawn_radius` oyun kuralı 1.21.11 sunucusunda tanınmıyor (birçok ad denendi). Hipotez test edilemedi. İkinci ölçüm: spawn (2560, 128, -624), oyuncu (2551, 125, -628), fark (−9, −3, −4). Kalan: sapmanın kaynağını başka bir yolla bulmak (spawn'ın oyuncuya uygulanış yolu, Paper'ın ilk giriş konumu) ya da tutarlı bir kural belirlemek. Önceki FAIL: taze dünyada level.dat spawn (2320, 65, -1716), botun ilk konumu (2327, 88, -1726). Eşitleme çağrısı (TfcChunkGenerator.spawn -> TfcSpawn.setWorldSpawn) kaydedilen spawn'ı değiştirmiyor. Muhtemel neden: Paper dünyayı oluştururken spawn'ı kendi aramasıyla yazıp, bizim çağrı ondan sonra gelmiş olabilir; ya da arama çağrısı dünya kaydedilmeden önce hiç çalışmıyor. Sonraki adım: setSpawnLocation'ın ne zaman ve hangi thread'de çağrıldığını loglayıp Paper'ın spawn kaydını doğrulamak; sonra tekrar kontrol.

- [x] Maksimum sağlık gösterimi kararı: TFC'nin kaynağındaki ölçek (ortalama beslenmede 850/1000) uygulanacak; planın "45/50" örneği kaynakla uyuşmadığı için düzeltilecek. Gerekçe: kaynağın kendisi doğru davranış; plan örneği yanlış.

## Plan durum satırlarından açık işler (M2–M6)

M2 (etkin milestone):
- [ ] **M2-CAL-02 HUD tarih öğesi.** Ekran üstü tarih için BetterHud metin öğesi (`tick: 20`) henüz üretilmiyor; satır [x] işaretli ama bu kontrol açık.
- [ ] **M2-CAL-01 takip işleri.** M2-CAL-03 yapılandırma (başlangıç günü, ay ve gün uzunluğu), uyku ile gün atlama ve doDaylightCycle eşitlemesi; henüz görev olarak yok.
- [ ] **M2-WTH-02 sunucu kontrolü.** Soğuk zeminde kar katmanlarının doğru mevsimde oluşması, sıcakta erimesi; görsel kontrol listesi.
- [ ] **M2-WTH-04 gecikmeli kar telafisi.** Gecikmeli ile adım adım eşdeğerlik testi, yüklenen chunk'ın kış sonrası telafisi, canlı yağmur testi (anlık ve zaman ortalaması).
- [ ] **M2-SEA-01 JMH bütçesi ve görsel kontrol.** 2.000 varyantlık kayıt eşitlemesi ve paket yeniden yazımı için JMH; görsel kontrol listesi.
- [ ] **M2-GEN-06 sunucu kontrolü.** Satır [x] işaretli ama durum satırında sunucu kontrolü açık; ayrıca yoğunluk kaverna ve beardifier (M2-GEN-09) ve diğer volkan örnekleyicileri (M2-GEN-14).
- [ ] **M2-GEN-07 sunucu ve JMH.** Sunucu kontrolü ve worldgen JMH bütçesi açık; iklim çağrı noktaları hâlâ `ChunkClimate.NEUTRAL`; Bukkit biyom kaydı bağlaması.
- [ ] **M2-GEN-08 kalan.** TFC blok seti (kaya çakılı, toprak varyantları), buz tabakaları ve deniz buzu, lav akıntıları, diğer biyom yapıcıları, sunucu kontrolü.
- [ ] **M2-GEN-09 kalan.** Vanilla mağara tünelleri, kanyonlar, aquifer bariyer gürültüsü (şimdilik sıfır), kazılmış yüzey altında çim kuralı, sunucu kontrolü.
- [ ] **M2-GEN-10 kalan.** Disk, boru ve kaolin damarları (yeni görev), montan ikinci geçiş, 100 chunk dağılım istatistiği, sunucu kontrolü ve görsel kontrol.
- [ ] **M2-GEN-13 kalan.** CE bitki, ekin ve mercan blokları; sarmaşık, yosun, epifitler, sürünen bitkiler, kaktüsler, okyanus bitkileri, atol mercanları, meyve ağaçları ve çalılar; orman zemin örtüsü; sunucu kontrolü.
- [ ] **M2-GEN-14 kalan.** Kalderalar dışındaki volkan örnekleyicileri, sıcak kaynaklar, buzullar ve diğer özellikler.

M3:
- [ ] **M3-SURV-01 kalan.** `tfp:food_stamp` için datagen çıktısı yok; yiyecek tooltip satırının paket lore ile çizilmesi.
- [ ] **M3-SURV-02 kalan.** Yeme eylemi (Paper), bozuk yiyecek etkileri, tokluk eşitlemesi, kalıcılık.
- [ ] **M3-SURV-03 kalan.** İçme eylemi, su kaynakları ve tuzlu su, testler, PlayerData kalıcılığı.
- [ ] **M3-SURV-04 kalan.** Paper hasar kancası ve dövme bonusu, silah ve varlık hasar tipi eşlemesi, HealthState kalıcılığı, gösterim.
- [ ] **M3-SURV-05 kalan.** Item PDC ve `item_size` çözümü, kap ekleme kontrolleri (sandık, kap, varil, çanta).
- [ ] **M3-HEAT-01 kalan.** Item PDC ısı durumu, CE ısı görseli (`range_dispatch`), tooltip, kaba aktarım, yanma hasarı.
- [ ] **M3-HEAT-02 kalan.** Ateş çukuru bloğu ve varlığı, yakıt öğeleri ve etiketleri, çakmak taşı Paper bağlantısı.
- [ ] **M3-HEAT-03 kalan.** Blok varlıkları ve denetleyicileri, CE görsel ve GUI, konteyner, kül, ısıtma tarifleri.
- [ ] **M3-HEAT-04 kalan.** Yerleştirilen bloklar ve sönük meşale değişimi, Paper ışık, CE görselleri, fener yakıt döngüsü.
- [ ] **M3-GATH-01 kalan.** `tfp:pickupable` blok kancası, CE blokları ve buton durumu görselleri, explosion_decay kuralı.
- [ ] **M3-GATH-02 kalan.** Paper kırma ve sağ tık kancası, CE blokları ve görselleri, öz testi, görsel kontrol.
- [ ] **M3-KNAP-01 kalan.** Paper kılavuz ekranı (5x5 düğme), CE tarifleri ve aletleri.
- [ ] **M3-TOOL-01 kalan.** Paper kırma ve kullanma kancaları, CE alet öğeleri (`break_power`, `correct_tools`), mızrak atışı, taş alet görselleri.
- [ ] **M3-PHYS-01 kalan.** Çökme koşucusu (async karar, sync uygulama), blok destek verisi.
- [ ] **M3-PHYS-02 kalan.** Kayma koşucusu, komşu güncelleme kancaları, blok destek verisi, CE blokları.
- [ ] **M3-PHYS-03 kalan.** Bağlantı koşucusu, CE kiriş blokları ve görselleri, yerleştirme ve kırma zinciri testi.
- [ ] **M3-POT-01 kalan.** Çömlek fırını bloğu, yerleştirme ve tutuşturma, içerik ısısı, CE görselleri.
- [ ] **M3-POT-02 kalan.** Paper kap ve kalıp yerleştirme ve envanteri, CE öğeleri (`range_dispatch`), yerli bakır yolu testi.
- [ ] **M3-POT-03 kalan.** Büyük kap ve testi yerleştirme ve envanter, dünyaya dökme, redstone ve huni otomasyonu, CE blokları.
- [ ] **M3-POT-04 kalan.** CE yığın ve saman blokları, yağmur beslemesi, kuruma dokusu ve saman sunucu kontrolü.
- [ ] **M3-PROC-01 kalan.** Değirmen bloğu ve blok varlığı, el taşı etkileşimi, dönüş ağı bağlantısı, GUI, CE görseli.
- [ ] **M3-PROC-02 kalan.** Paper etkileşimi (bıçak, dokuma tezgâhı, dikiş), GUI ve dikiş konteyneri, CE görselleri.
- [ ] **M3-PROC-03 kalan.** Varil bloğu ve etkileşimi, 22 anlık tarif ve 331 tarif yükleme kontrolü, `on_seal` yiyecek özellikleri.
- [ ] **M3-PROC-04 kalan.** Kütük yığını ve kömür çukuru blokları, yerleştirme ve tutuşturma Paper bağlantısı.
- [ ] **M3-PROC-05 kalan.** Kompost bloğu ve blok varlığı durumu, Paper etkileşimi, hopper otomasyonu, çürüme, CE görselleri.
- [ ] **M3-FARM-01 kalan.** Tarla bloğu ve blok varlığı durumu, Paper nem kaynağı (async), gübre öğesi, CE görselleri.
- [ ] **M3-FARM-02 kalan.** Ekin blokları (tüm şekiller), Paper büyüme bağlantısı ve blok varlığı, tohum (`block_item`), yayılan meyve, CE görselleri.
- [ ] **M3-FARM-03 kalan.** Yayılan çalı telafisi, çalı blokları ve yayılan dal geometrisi, Paper büyüme bağlantısı, CE görselleri.
- [ ] **M3-GUI-02 kalan.** 2.912 tarif sayısı, düz tarifler için CE YAML, `PrepareItemCraftEvent` kesişimi, vanilla tarif sızıntı testi, tezgâh GUI.
- [ ] **M3-PAN-01 kalan.** Tarama kaybı tabloları (datagen), eleme sandığı bloğu, CE görselleri, Paper bağlantısı (dakika başı sürücü, 20 tickte bir toplama), telafi.

M4:
- [ ] **M4-MET-01 kalan.** CE metal öğeleri, kayıt bağlantısı, ısı tooltip'leri, showcase.
- [ ] **M4-MET-02 kalan.** Paper sıvı bağlantısı, alaşım tarifleri (CE/Paper veri), alaşım GUI'si veya gösterimi.
- [ ] **M4-MET-03 kalan.** Paper döküm bloğu ve kalıp bağlantısı, CE öğeleri, kalıp kırılma görselleri, GUI.
- [ ] **M4-ANV-01 kalan.** Örs bloğu, dövme GUI'si, altın dövme sıraları, kaynak, config değerleri için Paper bağlantısı.
- [ ] **M4-ANV-02 kalan.** Örs bloğu ve CE görselleri, plan seçimi ve glif kaplamalı dövme GUI'si, çekiç, örs üstü öğe görselleri, kaynak (flux yuvası), bonus config.
- [ ] **M4-FORGE-01 kalan.** Ocak ve körük blokları ve CE görselleri, Paper tick veya telafi tetikleyicisi, GUI, yalıtım (`#tfc:insulation`) ve çok bloklu yapı taraması.
- [ ] **M4-CRUC-01 kalan.** Pota, kanal ve kalıp tablosu blokları, CE görselleri, GUI, Paper sıvı bağlantısı ve telafi, eritme.
- [ ] **M4-BLOOM-01 kalan.** Çok bloklu tarama (taban, baca, kapı), körük bloğu ve CE görselleri, GUI veya gösterim; katalizör girişinin TFC ile doğrulaması.
- [ ] **M4-BLAST-01 kalan.** Çok bloklu tarama, yüksek fırın bloğu ve CE görselleri, GUI, Paper tick veya telafi (TFC'de yakıt 1x yanar; telafi formu buna göre).
- [ ] **M4-TOOL-02 kalan.** CE metal alet ve zırh öğeleri, kayıt bağlantısı, tarifler, tooltip'ler, Paper hasar ve kırma hızı kancaları.
- [ ] **M4-PROS-01 kalan.** Propick öğesi ve CE görselleri, dünya taraması, sonuç mesajı (actionbar veya sohbet), `/tfp propick`.
- [ ] **M4-CHIS-01 kalan.** CE keski öğesi ve blok bağlantısı, mod döngüsü tetikleyicisi (sneak + el değiştirme), düzleştirme ve merdiven görselleri, Paper bağlantısı.
- [ ] **M4-WEATH-01 kalan.** Paper hava bağlantısı, telafi tetikleyicisi (chunk yükleme veya etkileşim), CE oksitlenme durumları, lamba bloku ve görselleri, lamba GUI'si.

M5:
- [ ] **M5-ENT-03 kalan.** `RegisterSpawnPlacementsEvent` bağlantısı, chunk başı vanilla üst sınırı (TFC kodu değil), BetterModel varlık görünümü.
- [ ] **M5-LIV-01 kalan.** Varlık çerçevesi bağlantısı (PDC'de taşıyıcı durumu), BetterModel görselleri, besleme ve çiftleşme etkileşimleri, chunk yüklemede takvim telafisi.
- [ ] **M5-LIV-02 kalan.** Taşıyıcının ürün tick'i (PDC), süt, kırpma ve yumurta olayları, `markElderlyIfDue`, kırpılmış bölüm görünümü.
- [ ] **M5-MNT-01 kalan.** Binek varlığı ve öznitelik bağlantısı, eyer ve binme kontrolleri, BetterModel görselleri, binme AI'si, eşya ve yük (sandık, yük ağırlığı), genetik boyut.
- [ ] **M5-HUNT-01 kalan.** Av yaratığı AI'si (M5-WILD-01), mızrak ve düşürme bağlantısı (varlık, ölüm, kasaplık), et ve deri öğeleri ve modeller, loot sayımı.
- [ ] **M5-FISH-01 kalan.** Olta ve tuzak öğeleri ve blokları, sudaki ve biyomdaki veriler (sonsuz su etiketi, olta kademesi), loot zarı, tuzak blokunun CE görselleri.

M6:
- [ ] **M6-MECH-01 kalan.** Dört döngü durumu için `RotationNetworkTest` (beşinci yüz geçirgen, dik ters çevirici; üretim türü değil), blok ve blok varlığı bağlantısı (`RotatingBlockEntity`).
- [ ] **M6-MECH-02 kalan.** Kaynak ve blok varlığı bağlantısı (40 tick zamanlama, akış ve bıçak envanteri), rüzgâr çekişleri için iklim tohumu, Paper hava bağlantısı.
- [ ] **M6-GLASS-01 kalan.** Cam havuzu ve sıcak cam blok varlığı bağlantısı (100 tick dinlenme, TFC blok durumları), üfleme etkileşimi ve GUI adımları, üfleme kırılma şansı.
- [ ] **M6-DECO-01 kalan.** Renderer tabanlı çit ve duvarlar, tabela varlıkları, blok mantığı, CE görsel kontrolü (yarım ve merdiven şablonları), GUI tabloları; checkout eksikken üreteç sessiz atlıyor.
- [ ] **M6-DECO-02 kalan.** Görseller ve blok mantığı, CE blok girdisi üreteci, merdiven, yarım ve duvar şablon kontrolü, GUI tabloları, ham ve düz çamur tuğlaları, görsel bütçe.
- [ ] **M6-DECO-03 kalan.** CE görselleri (`entity_renderer` ile lamba ve zincir), lamba ışık ve açık durumu, kapak ve zincir mantığı, şablon kontrolleri, GUI tabloları.
- [ ] **M6-DECO-04 kalan.** CE blok girdileri ve görseller, blok mantığı ve büyüme bağlantısı (yaşam döngüsü golden testleri), meyve yiyecek öğeleri, GUI tabloları.
- [ ] **M6-FLUID-01 kalan.** Kap blok ve blok varlığı bağlantısı, Paper sıvı işleme (kova, `FluidHelpers`), nehir akışı farkı, CE görselleri, GUI.
- [ ] **M6-ADV-01 kalan.** Paper tetikleyici ve ödül bağlantısı, oyun içi bildirim (toast veya mesaj), ölçütlerin özelliklere eşlenmesi, yerelleştirme anahtarları, GUI.
- [ ] **M6-CMD-01 kalan.** `/tfp` altında Brigadier kaydı (`tfc` kökü `tfp` olur), yetki kontrolleri (seviye 2), dünya erişimi (yüzey yükseklikleri, blok sınıflandırma).
- [ ] **M6-GUIDE-01 kalan.** Kitap görünümü veya GUI (biçim açık), diğer diller, Paper öğe ve blok bağlantıları, CE kitap öğesi, sayfa çizimi.
- Regression run, 2026-10-19 (not a new feature): HEAD 4df5ee4, `:itest:runSelfTest -Pmc=1.21.11 -Pplatform=paper` on Paper 1.21.11-132, Java 25.0.4.1: 16 cases, 15 passed, 0 failed, 1 skipped (inst.profile_restart, needs a second start in the same run dir, so not verified). SELFTEST_EXIT=0. Report: itest/build/selftest-run/paper-1.21.11/selftest-report.xml.
- Restart verification, 2026-10-19 (not a new feature): HEAD 54ace07 (code identical to 4df5ee4; the record commit only). `:itest:runSelfTest -Pmc=1.21.11 -Pplatform=paper` run a second time on the same run directory (the first start's marker `plugins/TerraFirmaPluginSelfTest/profile-restart.marker` was present): 16 cases, 16 passed, 0 failed, 0 skipped. inst.profile_restart PASSED: restart reproduced 1.21/level-3a/nether-end-off OK, 912 free, 36 used by other packs, 341 needed (pool 1289). SELFTEST_EXIT=0. Log: scratchpad/selftest-restart.log; report: itest/build/selftest-run/paper-1.21.11/selftest-report.xml.
- [ ] **M2-SEA-02 sonbahar tonu görsel kontrolü (spawn noktası)**: headless sunucuda spawn (-290.5, 62, -182.5) 1 Ekim'de (3.2 C) çekildi; çimen hâlâ parlak yeşil, sonbahar tonu görünmüyor. Önceki sonbahar görüntüleri (`scratchpad/autumn6/`) başka noktalardan. Açık: soğuk ve kuru bir noktada tonun görünmesini doğrulamak (M2-SEA-02 ve M2-CLIM-03 bağlantılı).
- [ ] **M2-SEA-02 sonbahar tonu, soğuk ve kuru nokta (sonuç)**: (-768, 63, 768), 1 Ekim, 3.5 C, 172 mm; ekran görüntüsü `scratchpad/shots/autumn-cold.png`. Çimen belirgin sonbahar tonu göstermiyor (yalnızca biraz donuk zeytin yeşili). Işık ve saat farklı olduğu için kontrollü karşılaştırma değil. Açık: aynı anda, aynı ışıkta yaz ve sonbahar karşılaştırması ve grass renk tablosunun sunucuda doğrulanması.
- [ ] **M2-SEA-02 sonbahar tonu, kontrollü karşılaştırma (sonuç, olası sorun)**: (-768, 768) noktasında yaz (12.5 C, 103 mm) ve sonbahar (5.1 C, 186 mm) çekimleri (`scratchpad/shots/tint-summer.png`, `tint-autumn.png`) piksel düzeyinde çimende aynı çıktı (fark 0). Sıcaklık ve yağış değişmesine rağmen çimen rengi değişmedi; bu, bu koşuda mevsimsel çimen tonunun uygulanmadığını düşündürüyor. Su yalnızca hafif değişti. HUD tarihi okunamadı. Açık: sunucu tarafında çimen biyom rengi güncellemesinin (seasonal biome remap ve istemci chunk yenileme) doğrulanması ve hata ayıklanması.
- Tint investigation (2026-10-19, `docs/porting/tint-investigation.md`): cause not confirmed; both shots show the vanilla plains grass (#91bd59 shaded), not a TFC variant. Candidate: a stale in-flight computation at a phase change offers its resend after the cooldown is cleared, and the new table's resend is then refused by the 100-tick cooldown (`paper/.../climate/SeasonalBiomeService.java:203-206`, `ResendLimiter.java:52`). No code change made; confirm with a unit test of the race or a headless `/tfp debug` check across a month change.
- Tint race (2026-10-19, `SeasonalResendRaceTest`): reproduced by the unit test (a stale January computation's resend consumed the cooldown, so the February resend was refused: `pendingResends=0`). Fixed in `paper/src/main/java/dev/tfp/paper/climate/SeasonalBiomeService.java:210-218` (a stale computation re-requests the chunk for the current phase instead of offering; the current-phase computation offers). Still open: the server check (headless month change with the grass tint visible) is required to close this gap.
- [ ] **M2-SEA-02 tint, sunucu doğrulaması (başarısız)**: resend yarışı düzeltmesi (1d72fd6) sonrası headless sunucuda haziran ve ekim çimen ortalamaları birebir aynı (86.13, 112.07, 53.45); fark 0, çimen hâlâ vanilla plains gölgesi. Sunucu logunda hiç "seasonal" satırı yok. Olası neden: mevsimsel biyom servisi bu sunucuda hiç başlamıyor veya datapack biyomları yüklenmiyor. Açık: servisin başlatıldığının ve datapack biyomlarının sunucuda yüklendiğinin doğrulanması.
- [ ] **M2-SEA-02 tint, ikinci kontrol (2026-10-19, `docs/porting/tint-investigation.md`)**: neden: `spikes/run/tint-fix/bukkit.yml` `worlds:` bölümü yoktu, varsayılan dünya vanilla arazisiydi, `SeasonalBiomeService.register` TFC üreteci bulamadığı için görünüm kurulmadı (`SeasonalBiomeService.java:168-171`). Çalışan koşularda (`install-fix`, `wth3`) bu blok var. Run klasörüne blok eklendi, sunucu yeniden başlatıldı: `tfp debug packets` `worlds=1 phase=OCTOBER`. Açık: aynı noktada çimen renginin ölçülmesi (mevcut vanilla chunk'lar kalır).
- [ ] **M2-SEA-02 tint, generator düzeltmesi sonrası ölçüm (sonuç: hâlâ değişmiyor, dünya şüpheli)**: `spikes/run/tint-fix` için bukkit.yml generator düzeltildi ve sunucu yeniden başlatıldı; `tfp debug packets` resend sayısının arttığını gösteriyor (haziran resent=225, ekim resent=450). Ama aynı noktada haziran ve ekim çimen ortalamaları yine birebir aynı (86.1, 112.1, 53.4). Olası neden: bu dünyanın chunk'ları generator düzeltmesinden önce vanilla üreteçle üretilmiş olabilir. Açık: temiz (yeni üretilmiş) bir TFC dünyasında haziran ve ekim ölçümünün tekrarlanması.
- [ ] **M2-SEA-02 tint, taze dünya denemesi (geçersiz ölçüm)**: taze run klasöründe ilk açılışta jar eksikti ("Could not set generator for default world 'world': Plugin 'TerraFirmaPlugin' does not exist"); dünya o açılışta oluşmuş olabilir, jar eklenip yeniden başlatılınca aynı dünya kullanılmış olabilir. Ölçüm: çimen (20,600) haziran (46,77,43), ekim (53,82,49); fark +7/+5/+6, aynı ton, yalnızca parlaklık. Bu bir sonbahar tonu kanıtı değil ve dünya üreteci doğrulanmadığı için geçersiz. Açık: jar run klasöründe ilk açılıştan önce olacak şekilde taze dünyanın tekrar ölçülmesi ve "Could not set generator" satırının olmadığının kontrolü.
- [ ] **M2-SEA-02 tint, geçerli taze dünya ölçümü (kısmi değişim, sonbahar tonu yok)**: jar ilk açılıştan önce plugins klasöründe; "Could not set generator" yok; "Found new data pack TerraFirmaPlugin/tfp_seasonal_biomes" var. Log: haziran `phase=JUNE tables=625 resent=0`, ekim `phase=OCTOBER tables=225 pendingResends=0 resent=225`. Çimen (100-200, 440-540): haziran (46.6, 77.9, 43.2), ekim (49.3, 73.7, 48.5); fark R +2.7, G -4.2, B +5.3; ton 114 -> 118 derece. Değişim var ama küçük ve sıcak sonbahar tonu değil. Açık: 5.1 C ve 186 mm için beklenen TFC çimen renginin (grass colormap) hesaplanıp sunucu değeriyle karşılaştırılması; ölçümün aynı ışıkta ve daha sert bir soğuk noktada tekrarı.
