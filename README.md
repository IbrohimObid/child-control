# Bola Nazorati

Bolaning Android telefonini ota-ona o'z qurilmasidan Telegram bot orqali boshqaradi:
YouTube va o'yinlarni bloklash, istalgan ilovani bloklash, hamda butun telefonni **soxta o'chirish** (qop-qora ekran — bola uchun telefon o'chib qolgandek). Botga bir nechta qurilma **ulanish kodi** bilan ulanishi mumkin.

## 1. APK yig'ish (GitHub orqali, kompyutersiz)

1. GitHub'da yangi **private** repo oching.
2. Shu papkadagi hamma faylni repoga yuklang (Add file → Upload files; `.github` papkasi ham bo'lsin).
3. **Actions** bo'limida «APK yig'ish» o'zi ishga tushadi (~5 daqiqa).
4. Tugagach, run'ni oching → pastda **Artifacts** → `BolaNazorati-apk` → zip ichida `app-release.apk`.

Android Studio orqali ham yig'sa bo'ladi: papkani oching → Build → Build APK.

## 2. Telegram bot

1. Telegram'da @BotFather → `/newbot` → nom bering → **token**ni oling.
2. Har bir bola telefoni uchun alohida bot oching.

## 3. Bolaning telefoniga o'rnatish

1. APK'ni o'rnating (noma'lum manbalarga ruxsat bering).
2. Ilovani oching → token va PIN kiriting → **Saqlash**.
3. Ekrandagi ❌ belgili bandlarning hammasini yoqing:
   - **Android 13+**: avval Sozlamalar → Ilovalar → Bola Nazorati → ⋮ → «Cheklangan sozlamalarga ruxsat berish».
   - Maxsus imkoniyatlar → Bola Nazorati → Yoqish.
   - **Boshqa ilovalar ustida ko'rsatish → Yoqish** (qora ekran uchun shart).
   - Qurilma administratori → Yoqish.
   - Batareya tejash → Cheklanmagan.
   - Xiaomi/Redmi: Xavfsizlik → Avtozapusk → Bola Nazorati.
4. Ekranda **🔑 Ulanish kodi** ko'rinadi — uni eslab qoling.
5. **🔒 Qulflash va chiqish** tugmasini bosing.

## 4. Botga ulanish (istalgan qurilmadan)

Telegram'ni oching (telefon, planshet, kompyuter — farqi yo'q), botga kiring va yozing:

```
/ulan KOD
```

`KOD` — ilovada ko'rsatilgan 6 xonali ulanish kodi. Bir nechta qurilma ulanishi mumkin (ota va ona alohida). Kodni ko'rish/yangilash: `/kod`, `/kod yangi`.

## 5. Boshqarish buyruqlari

| Buyruq | Nima qiladi |
|---|---|
| 🔌 Telefonni o'chirish, `/lock` | Butun telefonni qora ekran bilan qulflaydi — o'chib qolgandek |
| 🔆 Telefonni yoqish, `/unlock` | Qora ekranni olib tashlaydi |
| 🚫 YouTube blok, `/yt_off` | YouTube'ni bloklaydi |
| ✅ YouTube ruxsat, `/yt_on` | Muddatsiz ruxsat |
| ⏱ 30 daqiqa, `/yt_on 45` | Vaqtinchalik ruxsat, vaqt tugagach o'zi yopiladi |
| 🎮 O'yinlar blok, `/oyin_off` | Barcha o'yinlarni bloklaydi |
| 🎮 O'yinlar ruxsat, `/oyin_on` | O'yinlarga ruxsat |
| 📊 Holat, `/holat` | Telefon, YouTube, o'yin, himoya, batareya holati |
| `/ilovalar` | O'rnatilgan ilovalar ro'yxati |
| `/block paket`, `/unblock paket`, `/royxat` | Istalgan ilovani (TikTok va h.k.) bloklash |
| `/kod`, `/qurilmalar` | Ulanish kodi va ulangan qurilmalar |
| `/himoya_off`, `/himoya_on` | Telefonda ilovani o'chirish yoki sozlash |

Bola bloklash xizmatini yoki administratorni o'chirsa, hamma ulangan qurilmaga ⚠️ ogohlantirish keladi.

## Nimalar bloklanadi

- **Qora ekran rejimi** — butun telefon ishlamaydi, teginishlar o'tmaydi, ekran o'chgandek qora.
- **YouTube** — ilova, YouTube Music, ReVanced/Vanced, NewPipe, LibreTube; brauzerda `youtube.com`/`youtu.be` ochilsa.
- **O'yinlar** — manifestida «o'yin» toifasi ko'rsatilgan ilovalar. Toifani bildirmagan o'yinlarni `/block` bilan qo'shing.
- **Istalgan ilova** — `/block` orqali.
- Sozlamalarda «Bola Nazorati» chiqqan sahifalar (o'chirish, administrator). PIN kiritilgach 10 daqiqa yoki `/himoya_off` bilan ochiladi.

### Qora ekranni telefonning o'zidan ochish (zaxira)

Internet bo'lmasa: qora ekranning **chap yuqori burchagiga 4 soniya ichida 8 marta** tez teging — qulf ochiladi.

## Cheklovlar

- Telegram/Instagram ichidagi YouTube pleyeri bloklanmaydi.
- Qora ekran rejimida bildirishnoma pardasi (yuqoridan tortish) qisman ochilishi mumkin; uskuna tugmalari (ovoz/quvvat) ishlaydi.
- Xavfsiz rejim (Safe mode) yoki zavod sozlamalariga qaytarish himoyani chetlab o'tadi.
- Bitta botni faqat bitta bola telefoni ishlatsin; har bir bolaga alohida bot oching.
- Imzo kaliti (`app/nazorat.jks`) va paroli `app/build.gradle` ichida — repo **private** bo'lsin. Yangi versiyani ustidan o'rnatish uchun shu kalit kerak.
