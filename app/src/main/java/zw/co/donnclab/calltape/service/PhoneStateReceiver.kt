package zw.co.donnclab.calltape.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            Log.i("PhoneStateReceiver", "Received state: $state, number: $number")
            
            val serviceIntent = Intent(context, CallTranscriptionService::class.java).apply {
                putExtra("state", state)
                putExtra("number", number)
            }
            
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context?.startForegroundService(serviceIntent)
                } else {
                    context?.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("PhoneStateReceiver", "Failed to start service from receiver", e)
            }
        }
    }
}
