# DurdurNet (Android starter)

Root gerektirmeyen Android uygulama iskeleti. GitHub Actions `debug APK` üretir.

## Şu an neler var?
- Android uygulama listesi ve seçilebilir kutular (yalnızca arayüz/yerel bellek).
- Android VPN izin ekranını açma ve `VpnService` örneği.
- Android'in uygulama ayarlarına kısayol.
- GitHub Actions ile APK derleme.

## ÖNEMLİ — Bu henüz çalışan bir firewall değildir
Bu ilk iskelet VPN tünelini oluşturur ancak IP paketlerini iletmez veya seçili uygulamalara göre filtrelemez. VPN'i başlatmak internet bağlantısını kesebilir. Test etmeden ana telefonunda VPN'i başlatma; yalnızca test cihazında dene. Gerçek uygulama engellemesi için paket okuma/yönlendirme, uygulama bazlı `addAllowedApplication`/`addDisallowedApplication` seçimi ve bağlantı yönetimi ayrıca tamamlanıp test edilmelidir.

Android'in normal uygulama izinleriyle diğer uygulamaları sistem genelinde tamamen devre dışı bırakmak mümkün değildir. Bu proje bunun yerine sistem uygulama ayarlarına yönlendirir.

## APK üretme
1. Bu klasörün içeriğini yeni bir GitHub deposuna yükle.
2. GitHub deposunda **Actions** sekmesini aç ve workflow çalışmasına izin ver.
3. **Actions → Build DurdurNet APK → Run workflow** seç.
4. Başarılı çalışmanın altındaki **Artifacts** bölümünden `DurdurNet-debug-apk` dosyasını indir.
5. ZIP'i açıp `app-debug.apk` dosyasını Android telefona kur.

## Teknik
- Kotlin, Android SDK 35, minSdk 26.
- GitHub Actions derlemesi.
