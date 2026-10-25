# Kaisertv 4.0 — Android TV

Kumanda odaklı, koyu/Netflix esintili yerel Android uygulaması. Yalnızca kullanma hakkınız olan içerik kaynaklarını ekleyin; uygulama kanal veya film sağlamaz.

## Özellikler
- Birden fazla M3U/M3U8 kaynağını ad vererek kaydetme ve uygulama açılışında otomatik yükleme
- Birden fazla Xtream Codes kaynağını ad vererek kaydetme ve uygulama açılışında otomatik yükleme
- Kaynakları yönetme: yeniden yükleme ve silme
- M3U/M3U8 URL'sinden liste yükleme
- Xtream Codes API ile canlı TV, film ve dizi kataloğu
- Dizi seçildiğinde sezon ve bölüm listesini Xtream `get_series_info` API'sinden yükleme ve bölümü oynatma
- Canlı TV, Filmler, Diziler, Favoriler, Son İzlenenler ve Tümü sekmeleri
- Canlı TV, Filmler ve Diziler için bağımsız kategori filtreleri
- M3U `group-title` ve Xtream kategori adlarını kullanma; kategori yoksa `Diğer`
- Sağlayıcı veriyorsa film/dizi afişlerini gösterme
- Favoriler, son izlenen 20 içerik ve kaynak listesi cihazda saklanır
- AndroidX Media3 ExoPlayer ile HTTP(S) akış oynatma
- Android TV kumandası için odaklanabilir kontroller ve yatay içerik satırları
- EPG dahil değildir

## Android Studio ile APK oluşturma
1. Android Studio'nun güncel kararlı sürümünü ve Android SDK Platform 35'i kurun.
2. ZIP'i çıkarın; Android Studio'da `AndroTVIPTV` klasörünü **Open** ile açın.
3. Gradle Sync'in bitmesini bekleyin. JDK 17 kullanın (Android Studio'nun gömülü JDK'sı uygundur).
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)** seçin.
5. APK'yı `app/build/outputs/apk/debug/app-debug.apk` konumundan Android TV'ye aktarın ve kurun. TV güvenlik ayarlarında bu dosya yöneticisi için bilinmeyen uygulama kurulumuna izin vermeniz gerekebilir.

## Bağlantı
- **M3U/M3U8:** `https://...` biçimindeki oynatma listesi URL'sini girin.
- **Xtream Codes:** Sunucu kök adresi, kullanıcı adı ve şifreyi girin. Uygulama `player_api.php` ile canlı, film ve dizi kataloglarını ister.
- Dizi seçince sezonlar/bölümler API'den alınır; bir bölüm seçilince `/series/` akış adresi Media3 ile açılır.

## Bilinen sınırlar
- EPG bilerek dahil edilmedi.
- Afişler sağlayıcının URL'si varsa gösterilir; bazı sağlayıcılar farklı alan adları kullanabilir.
- M3U kategorileri sağlayıcıya göre değişir; sınıflandırma grup ve ad metnine göre tahminidir.
- Xtream API sağlayıcıları arasında alan adı/yanıt biçimi farklılıkları olabilir. Bu durumda bazı diziler veya bölümler görünmeyebilir.
- Bazı akışlar kodek, DRM, sağlayıcı kısıtlaması veya süresi dolmuş bağlantılar nedeniyle oynatılamayabilir.
- Kaynak bilgileri uygulamanın özel yerel tercih alanında saklanır; uygulama verilerini silmek kaynak kayıtlarını da siler. Bu ZIP kaynak kodudur, hazır APK içermez.
