package dev.example.issue150.red

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.util.Log
import dev.example.issue150.red.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : Activity() {
    companion object {
        private val runNonce = UUID.randomUUID().toString()
        private var counter = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val marker = "appRed|${BuildConfig.BUILD_TYPE}|${BuildConfig.APPLICATION_ID}|$runNonce|${Process.myPid()}"
        binding.targetLabel.text = marker
        binding.emitMarker.setOnClickListener {
            counter += 1
            Log.i("I150_TARGET", "$marker|$counter")
        }
        binding.crashNow.setOnClickListener {
            throw IllegalStateException("I150_EXPECTED_CRASH:$runNonce")
        }
    }
}
