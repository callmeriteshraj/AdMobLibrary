package com.example.ads.util

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import com.example.ads.R

/**
 * Thread-safe, lifecycle-aware loading dialog displayed during on-demand ad fetching.
 * Features an automatic safety timeout to prevent permanent blocking if networks fail.
 */
class AdLoadingDialog(private val activity: Activity) {

    private var dialog: Dialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    /**
     * Displays the loading dialog on the UI thread.
     *
     * @param message Optional message to display on the dialog.
     * @param timeoutMillis Safety timeout duration after which the dialog automatically dismisses.
     * @param onTimeout Optional callback invoked if timeout occurs before manual dismissal.
     */
    fun show(
        message: String? = null,
        timeoutMillis: Long = 10000L,
        onTimeout: (() -> Unit)? = null
    ) {
        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            try {
                dismissInternal()

                val newDialog = Dialog(activity)
                newDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                val view = LayoutInflater.from(activity).inflate(R.layout.layout_dialog_ad_loading, null)
                
                if (message != null) {
                    val tvMessage = view.findViewById<TextView>(R.id.tvAdLoadingMessage)
                    tvMessage?.text = message
                }

                newDialog.setContentView(view)
                newDialog.setCancelable(false)
                newDialog.setCanceledOnTouchOutside(false)
                newDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                newDialog.show()

                this.dialog = newDialog

                timeoutRunnable = Runnable {
                    if (isShowing()) {
                        AdsLogger.w(AdsLogger.TAG_CORE, "AdLoadingDialog timed out after ${timeoutMillis}ms.")
                        dismissInternal()
                        onTimeout?.invoke()
                    }
                }
                mainHandler.postDelayed(timeoutRunnable!!, timeoutMillis)

            } catch (e: Exception) {
                AdsLogger.w(AdsLogger.TAG_CORE, "Failed to show AdLoadingDialog: ${e.message}")
            }
        }
    }

    /**
     * Dismisses the dialog safely on the main thread.
     */
    fun dismiss() {
        mainHandler.post {
            dismissInternal()
        }
    }

    private fun dismissInternal() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        try {
            if (dialog?.isShowing == true && !activity.isFinishing && !activity.isDestroyed) {
                dialog?.dismiss()
            }
        } catch (e: Exception) {
            AdsLogger.w(AdsLogger.TAG_CORE, "Exception dismissing AdLoadingDialog: ${e.message}")
        } finally {
            dialog = null
        }
    }

    fun isShowing(): Boolean = dialog?.isShowing == true
}
