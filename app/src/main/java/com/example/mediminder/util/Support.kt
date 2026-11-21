package com.example.mediminder.util

import android.content.Context
import android.content.Intent
import com.example.mediminder.BuildConfig
import com.example.mediminder.R

/** Getting help: the developer's address, and sending the problem log to it. */
object Support {
    const val EMAIL = "m.a.hamza362@gmail.com"

    /**
     * Opens the person's email app, addressed to the developer, with room to say what happened
     * and the problem log (app version, phone, crashes and errors) underneath.
     */
    fun reportProblem(context: Context) {
        val ctx = AppLocale.localized(context)
        val body = ctx.getString(R.string.report_problem_intro) + "\n\n\n" + ctx.getString(R.string.report_problem_log_heading) + "\n" + ErrorLog.report()
        val send = Intent(Intent.ACTION_SEND)
            .setType("message/rfc822")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.report_problem_subject, BuildConfig.VERSION_NAME))
            .putExtra(Intent.EXTRA_TEXT, body)
        context.startActivity(Intent.createChooser(send, ctx.getString(R.string.report_problem)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
