# 🎵 Aurora Player

เครื่องเล่นเพลงออฟไลน์ที่เน้นความลื่นไหลของภาพเคลื่อนไหวและการเล่นต่อเนื่องเบื้องหลัง  
สร้างด้วย **Kotlin Multiplatform + Compose Multiplatform** — codebase เดียว ส่งมอบทั้ง Android และ Windows

## คุณสมบัติ
- เล่นไฟล์เพลงในเครื่อง พร้อมปกอัลบั้ม ชื่อเพลง ศิลปิน และอัลบั้ม
- เล่นต่อเนื่องแม้ปิดหน้าจอ/ออกจากแอป ผ่าน `MediaSessionService`
- ควบคุมจาก Notification, Lock Screen, หูฟัง Bluetooth และ Android Auto
- Waveform visualizer จากสเปกตรัมเสียงจริง (FFT)
- พื้นหลังไล่สีอัตโนมัติที่สกัดจากปกอัลบั้ม (Dynamic Palette)
- ภาพเคลื่อนไหวทุกส่วนออกแบบให้ทำงานใน Draw Phase เท่านั้น → ไม่หน่วงแม้ใช้งานร่วมกับแอปอื่น

## ความต้องการของระบบ
| แพลตฟอร์ม | ข้อกำหนด |
|---|---|
| Android | 7.0 (API 24) ขึ้นไป — เอฟเฟกต์เบลอเต็มรูปแบบที่ Android 12+ |
| Windows | Windows 10/11 64-bit |
| Build | JDK 17, Android SDK 36, WiX v3 (เฉพาะการทำ EXE) |

## การ Build
```bash
git clone https://github.com/<user>/AuroraPlayer.git
cd AuroraPlayer

./gradlew :composeApp:assembleRelease      # APK
./gradlew packageReleaseExe                # EXE (ต้องรันบน Windows)
./gradlew run                              # ทดสอบบนเดสก์ท็อป
```

## สถาปัตยกรรม
```
UI (Compose, commonMain)
        ↓ observe StateFlow
AudioController  ← abstraction (Dependency Inversion)
   ├─ Android : Media3AudioController → MediaSessionService → ExoPlayer
   └─ Desktop : JavaFxAudioController → javafx.scene.media.MediaPlayer
```

## License
MIT
