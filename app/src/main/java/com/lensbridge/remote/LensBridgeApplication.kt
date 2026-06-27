package com.lensbridge.remote

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import com.lensbridge.remote.adb.AndroidKeystorePrivateKeyStore

class LensBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KadbCert.configure(AndroidKeystorePrivateKeyStore(this))
        KadbCert.ensureReady()
    }
}
