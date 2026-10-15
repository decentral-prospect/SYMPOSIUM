package com.decentralprospect.symposium

import android.app.Application

class SymposiumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocalization.initialize(this)
    }
}
