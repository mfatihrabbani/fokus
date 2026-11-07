# Fokus

Aplikasi Android sederhana untuk mengurangi distraksi: blokir website dan aplikasi.

## Ambil APK

Setiap push, GitHub Actions membuat APK. Buka tab **Actions** → run terbaru →
unduh artifact **Fokus-apk** (berisi `app-release.apk`).

## Cara pakai

1. **Website**: tulis domain satu per baris (misalnya `x.com`, `twitter.com`,
   `youtube.com`), lalu tekan **Simpan & blokir website** dan setujui izin VPN.
   Satu domain memblokir semua halaman dan subdomainnya (`x.com/apa`,
   `m.youtube.com`, dan seterusnya). VPN-nya hanya lokal di HP; hanya pencarian DNS
   yang diperiksa.
2. **Aplikasi**: centang aplikasi yang mau diblokir, tekan tombol aksesibilitas,
   lalu aktifkan **Fokus**. Kalau Android 13+ menolak ("setelan terbatas"), buka
   Info aplikasi Fokus → menu ⋮ → **Izinkan setelan terbatas**, lalu ulangi.

Tips: supaya blokir website tetap jalan setelah HP restart, aktifkan
Setelan → Jaringan → VPN → Fokus → **VPN selalu aktif**.

## Catatan

- Tab yang sudah terbuka mungkin masih jalan sebentar karena cache DNS; tutup lalu
  buka lagi browsernya.
- Kalau di Chrome "DNS aman" diatur ke penyedia tertentu, ubah ke **Otomatis**
  atau matikan supaya blokir website tidak terlewati.
