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
        /*
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                builder.appendFactory(FencedCodeBlock::class.java) { _, _ ->
                    CopyContentsSpan()
                }
                builder.appendFactory(FencedCodeBlock::class.java) { _, _ ->
                    CopyIconSpan(context)
                }
            }
        })
        */
        .build()

    /*
    // Source: https://github.com/noties/Markwon/blob/2ea148c30a07f91ffa37c0aa36af1cf2670441af/app-sample/src/main/java/io/noties/markwon/app/samples/CopyCodeBlockSample.kt
    class CopyContentsSpan : ClickableSpan() {
        override fun onClick(widget: View) {
            val spanned = (widget as? TextView)?.text as? Spanned ?: return
            val start = spanned.getSpanStart(this)
            val end = spanned.getSpanEnd(this)

            // by default code blocks have new lines before and after content
            val contents = spanned.subSequence(start, end).toString().trim()

            // copy code here
            val clipboardService = widget.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardService.setPrimaryClip(ClipData.newPlainText(contents, contents))

            Toast.makeText(widget.context, R.string.chat_toast_copy_clipboard, Toast.LENGTH_LONG).show()
        }

        override fun updateDrawState(ds: TextPaint) {
            // do not apply link styling
        }
    }

    class CopyIconSpan(val context: Context) : LeadingMarginSpan {
        @SuppressLint("UseCompatLoadingForDrawables")
        private val icon: Drawable = context.getDrawable(R.drawable.ic_file)!!

        init {
            if (icon.bounds.isEmpty) {
                icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
            }
            icon.setTint(ColorUtil.getColorControlNormal(context))
        }

        override fun getLeadingMargin(first: Boolean): Int = 0

        override fun drawLeadingMargin(
            c: Canvas,
            p: Paint,
            x: Int,
            dir: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            first: Boolean,
            layout: Layout
        ) {

            // called for each line of text, we are interested only in first one
            if (!LeadingMarginUtils.selfStart(start, text, this)) return

            val save = c.save()
            try {
                // horizontal position for icon
                val w = icon.bounds.width().toFloat()
                val h = icon.bounds.height().toFloat()
                // minus quarter width as padding
                val left = layout.width - w - (w / 4F)
                c.translate(left, top.toFloat() + (h / 4F))
                icon.draw(c)
            } finally {
                c.restoreToCount(save)
            }
        }
    }
    */
}