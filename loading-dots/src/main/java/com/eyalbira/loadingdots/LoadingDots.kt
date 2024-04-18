package com.eyalbira.loadingdots

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.LinearLayout

/**
 *
 * Customizable bouncing dots view for smooth loading effect.
 * Mostly used in chat bubbles to indicate the other person is typing.
 *
 * Created by Eyal Biran (EyalBira @ Github) on 12/5/15.
 * Kotlin version by Looper (iamlooper @ Github).
 */
class LoadingDots @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    companion object {
        private const val DEFAULT_DOTS_COUNT = 3
        private const val DEFAULT_LOOP_DURATION = 600
        private const val DEFAULT_LOOP_START_DELAY = 100
        private const val DEFAULT_JUMP_DURATION = 400
    }

    private var mDots: MutableList<View> = mutableListOf()
    private var mAnimation: ValueAnimator? = null
    private var mIsAttachedToWindow = false

    var isAutoPlay = true
    var dotsColor = Color.GRAY
    var dotsCount = DEFAULT_DOTS_COUNT
    var dotSize = 0
    var dotSpace = 0
    var loopDuration = DEFAULT_LOOP_DURATION
    var loopStartDelay = DEFAULT_LOOP_START_DELAY
    var jumpDuration = DEFAULT_JUMP_DURATION
    var jumpHeight = 0

    private var mJumpHalfTime: Int = 0
    private lateinit var mDotsStartTime: IntArray
    private lateinit var mDotsJumpUpEndTime: IntArray
    private lateinit var mDotsJumpDownEndTime: IntArray

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.LoadingDots, 0, 0).apply {
            try {
                isAutoPlay = getBoolean(R.styleable.LoadingDots_LoadingDots_auto_play, true)
                dotsColor = getColor(R.styleable.LoadingDots_LoadingDots_dots_color, Color.GRAY)
                dotsCount =
                    getInt(R.styleable.LoadingDots_LoadingDots_dots_count, DEFAULT_DOTS_COUNT)
                dotSize = getDimensionPixelSize(
                    R.styleable.LoadingDots_LoadingDots_dots_size,
                    resources.getDimensionPixelSize(R.dimen.LoadingDots_dots_size_default)
                )
                dotSpace = getDimensionPixelSize(
                    R.styleable.LoadingDots_LoadingDots_dots_space,
                    resources.getDimensionPixelSize(R.dimen.LoadingDots_dots_space_default)
                )
                loopDuration =
                    getInt(R.styleable.LoadingDots_LoadingDots_loop_duration, DEFAULT_LOOP_DURATION)
                loopStartDelay = getInt(
                    R.styleable.LoadingDots_LoadingDots_loop_start_delay,
                    DEFAULT_LOOP_START_DELAY
                )
                jumpDuration =
                    getInt(R.styleable.LoadingDots_LoadingDots_jump_duration, DEFAULT_JUMP_DURATION)
                jumpHeight = getDimensionPixelSize(
                    R.styleable.LoadingDots_LoadingDots_jump_height,
                    resources.getDimensionPixelSize(R.dimen.LoadingDots_jump_height_default)
                )
            } finally {
                recycle()
            }
        }

        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM

        calculateCachedValues()
        initializeDots()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight + jumpHeight)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mIsAttachedToWindow = true

        if (isAutoPlay) {
            createAnimation()
            if (mAnimation?.isRunning != true) {
                mAnimation?.start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mIsAttachedToWindow = false
        mAnimation?.end()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        when (visibility) {
            VISIBLE -> {
                if (isAutoPlay) {
                    createAnimation()
                    if (mIsAttachedToWindow && mAnimation?.isRunning != true) {
                        mAnimation?.start()
                    }
                }
            }

            INVISIBLE, GONE -> mAnimation?.end()
        }
    }

    private fun createDotView(): View {
        return ImageView(context).apply {
            setImageResource(R.drawable.loading_dots_dot)
            (drawable as GradientDrawable).setColor(dotsColor)
        }
    }

    private fun createAnimation() {
        if (mAnimation == null) {
            calculateCachedValues()
            initializeDots()

            mAnimation = ValueAnimator.ofInt(0, loopDuration).apply {
                addUpdateListener { animation ->
                    val animationValue = animation.animatedValue as Int

                    if (animationValue < loopStartDelay) return@addUpdateListener

                    for (i in mDots.indices) {
                        val dot = mDots[i]
                        val dotStartTime = mDotsStartTime[i]

                        val animationFactor = when {
                            animationValue < dotStartTime -> 0f
                            animationValue < mDotsJumpUpEndTime[i] -> (animationValue - dotStartTime) / mJumpHalfTime.toFloat()
                            animationValue < mDotsJumpDownEndTime[i] -> 1 - (animationValue - dotStartTime - mJumpHalfTime) / mJumpHalfTime.toFloat()
                            else -> 0f
                        }

                        val translationY = -jumpHeight * animationFactor
                        dot.translationY = translationY
                    }
                }
                duration = loopDuration.toLong()
                repeatCount = Animation.INFINITE
            }
        }
    }

    fun startAnimation() {
        if (mAnimation?.isRunning == true) {
            // Already running.
            return
        }
        createAnimation()
        if (mIsAttachedToWindow && mAnimation?.isRunning != true) {
            mAnimation?.start()
        }
    }

    fun stopAnimation() {
        if (mAnimation != null) {
            mAnimation?.end()
            mAnimation = null
        }
    }

    private fun calculateCachedValues() {
        verifyNotRunning()

        val startOffset = (loopDuration - (jumpDuration + loopStartDelay)) / (dotsCount - 1)

        mJumpHalfTime = jumpDuration / 2

        mDotsStartTime = IntArray(dotsCount)
        mDotsJumpUpEndTime = IntArray(dotsCount)
        mDotsJumpDownEndTime = IntArray(dotsCount)

        for (i in 0 until dotsCount) {
            val startTime = loopStartDelay + startOffset * i
            mDotsStartTime[i] = startTime
            mDotsJumpUpEndTime[i] = startTime + mJumpHalfTime
            mDotsJumpDownEndTime[i] = startTime + jumpDuration
        }
    }

    private fun verifyNotRunning() {
        if (mAnimation != null) {
            throw IllegalStateException("Can't change properties while animation is running!")
        }
    }

    private fun initializeDots() {
        verifyNotRunning()
        removeAllViews()

        mDots.clear()
        val dotParams = LayoutParams(dotSize, dotSize)
        val spaceParams = LayoutParams(dotSpace, dotSize)
        repeat(dotsCount) { index ->
            val dotView = createDotView()
            addView(dotView, dotParams)
            mDots.add(dotView)

            if (index < dotsCount - 1) {
                addView(View(context), spaceParams)
            }
        }
    }
}
