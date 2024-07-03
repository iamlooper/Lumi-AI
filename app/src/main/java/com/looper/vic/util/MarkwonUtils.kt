package com.looper.vic.util

import android.content.Context
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import me.saket.bettermovementmethod.BetterLinkMovementMethod

object MarkwonUtils {
    fun getMarkdownParser(context: Context, textSize: Float): Markwon = Markwon.builder(context)
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(MovementMethodPlugin.create(BetterLinkMovementMethod.getInstance()))
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(
            JLatexMathPlugin.create(textSize) { builder ->
                builder.inlinesEnabled(true)
            }
        )
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(GlideImagesPlugin.create(context))
        .build()
}