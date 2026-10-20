package com.codblackops.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codblackops.mobile.dolphin.DolphinBridge

@Composable
fun AboutScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Tentang", style = MaterialTheme.typography.headlineSmall)
        SectionCard("Cara kerja") {
            NoteText(
                "Aplikasi ini berisi core emulator Dolphin (rilis 2609a) yang dikompilasi langsung ke dalam APK " +
                    "(libmain.so), bukan membuka aplikasi emulator lain. Launcher memindai file game, menulis " +
                    "konfigurasi ke Dolphin, lalu menjalankan EmulationActivity Dolphin di dalam proses yang sama.",
            )
            NoteText(
                "Kontrol: HUD sentuh, gyro dan gamepad mengirim input ke Wii Remote 1 emulasi lewat " +
                    "InputOverrider Dolphin (Classic Controller atau Wii Remote + Nunchuk, termasuk pointer IR " +
                    "dan gerakan goyang yang ditambahkan oleh patch proyek ini).",
            )
        }
        SectionCard("Emulasi vs rekompilasi") {
            NoteText(
                "Ini adalah EMULASI. CPU PowerPC Broadway dijalankan oleh JIT ARM64 Dolphin, yaitu rekompilasi " +
                    "dinamis blok kode saat runtime. Ini bukan rekompilasi statis.",
            )
            NoteText(
                "Rekompilasi statis (PowerPC → ARM64 sebelum dijalankan) sudah diteliti: rekompiler CPU untuk " +
                    "GameCube/Wii mulai tersedia sebagai proyek open-source, tetapi tidak menyertakan runtime untuk " +
                    "GPU Hollywood/GX, audio DSP, IOS, disc dan Wii Remote. Tidak ada runtime seperti itu untuk " +
                    "Black Ops. Arsitektur aplikasi memakai antarmuka GameBackend sehingga backend rekompilasi bisa " +
                    "ditambahkan nanti tanpa mengubah launcher, kontrol dan pengaturan.",
            )
        }
        SectionCard("Batasan yang diketahui") {
            NoteText("• Performa dan kompatibilitas Black Ops belum terverifikasi di Xiaomi Pad 6 oleh pembuat build ini.")
            NoteText("• FPS game dibatasi 30 oleh game itu sendiri; opsi 45/60 FPS dinonaktifkan.")
            NoteText("• Indikator amunisi/arah pandang di HUD tidak tersedia: alamat memori game belum dipetakan.")
            NoteText("• Mode Campaign/Multiplayer tidak terdeteksi otomatis; ganti tata letak dengan tombol MODE.")
            NoteText("• Multiplayer online tidak diintegrasikan: server resmi Nintendo WFC ditutup sejak 2014 dan layanan pengganti komunitas tidak dikonfigurasi oleh aplikasi ini.")
        }
        SectionCard("Lisensi & legal") {
            NoteText("Dolphin Emulator © Dolphin Emulator Project, GPL-2.0-or-later. Aplikasi ini juga dirilis di bawah GPL-2.0-or-later.")
            NoteText(
                "Proyek penggemar tidak resmi, tidak berafiliasi dengan Activision, Treyarch atau Nintendo. " +
                    "Tidak ada file game, BIOS atau firmware yang disertakan; gunakan dump dari disc milik Anda sendiri.",
            )
            InfoRow("Versi Dolphin", DolphinBridge.version())
        }
        Spacer(Modifier.height(24.dp))
    }
}
