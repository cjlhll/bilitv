package com.bili.bilitv.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.bili.bilitv.R
import com.bili.bilitv.VideoshotData
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import kotlinx.coroutines.*

/**
 * GSYVideoPlayer 预览窗口辅助类
 * 专用于处理 Bilibili 风格的拼图 Sprite Sheet 预览
 */
class GSYPreviewHelper(
    private val player: StandardGSYVideoPlayer,
    private val videoshotData: VideoshotData
) {
    private var previewLayout: View? = null
    private var previewImage: ImageView? = null
    private var previewTime: TextView? = null
    private var parentView: ViewGroup? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val bitmaps = mutableMapOf<Int, Bitmap>()
    private var isShowing = false

    init {
        // 尝试找到合适的父布局添加预览窗口
        // 通常 GSYVideoPlayer 的直接父布局或其内部的 Surface 容器适合
        parentView = player.parent as? ViewGroup ?: player
        initView(player.context)
        preloadImages(player.context)
    }

    private fun initView(context: Context) {
        previewLayout = LayoutInflater.from(context).inflate(R.layout.preview_window, parentView, false)
        previewImage = previewLayout?.findViewById(R.id.preview_image)
        previewTime = previewLayout?.findViewById(R.id.preview_time)

        // 初始添加到底层，但隐藏
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        // 初始位置不可见
        previewLayout?.visibility = View.INVISIBLE
        
        // 如果 parent 是 FrameLayout (GSY 默认是)，则直接添加
        (parentView as? ViewGroup)?.addView(previewLayout, params)
    }

    /**
     * 预加载拼图
     */
    private fun preloadImages(context: Context) {
        scope.launch(Dispatchers.IO) {
            videoshotData.image?.forEachIndexed { index, url ->
                try {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false) // 必须软Bitmap用于裁剪
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as BitmapDrawable).bitmap
                        bitmaps[index] = bitmap
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 显示预览
     * @param currentPosition 当前拖动的时间 (ms)
     * @param duration 视频总时长 (ms)
     * @param seekBarProgressRatio 进度条位置比例 (0.0 - 1.0) 用于定位弹窗
     */
    fun showPreview(currentPosition: Long, duration: Long, seekBarProgressRatio: Float) {
        if (previewLayout == null || duration <= 0) return
        
        if (!isShowing) {
            previewLayout?.visibility = View.VISIBLE
            isShowing = true
        }

        // 1. 更新时间显示
        previewTime?.text = stringForTime(currentPosition)

        // 2. 更新图片
        updateImage(currentPosition, duration)

        // 3. 更新位置
        updatePosition(seekBarProgressRatio)
    }

    private fun updateImage(currentPosition: Long, duration: Long) {
        val totalImages = (videoshotData.image?.size ?: 0) * videoshotData.img_x_len * videoshotData.img_y_len
        if (totalImages == 0) return

        val index = ((currentPosition.toDouble() / duration) * totalImages).toInt().coerceIn(0, totalImages - 1)

        val sheetSize = videoshotData.img_x_len * videoshotData.img_y_len
        val sheetIndex = index / sheetSize
        val internalIndex = index % sheetSize

        val row = internalIndex / videoshotData.img_x_len
        val col = internalIndex % videoshotData.img_x_len

        val sourceBitmap = bitmaps[sheetIndex]
        if (sourceBitmap != null) {
            val cropped = cropBitmap(
                sourceBitmap,
                col * videoshotData.img_x_size,
                row * videoshotData.img_y_size,
                videoshotData.img_x_size,
                videoshotData.img_y_size
            )
            previewImage?.setImageBitmap(cropped)
        }
    }

    private fun cropBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap? {
        return try {
            Bitmap.createBitmap(source, x, y, width, height)
        } catch (e: Exception) {
            null
        }
    }

    private fun updatePosition(ratio: Float) {
        previewLayout?.let { view ->
            view.post {
                val parentWidth = (view.parent as? View)?.width ?: 0
                val viewWidth = view.width
                
                // 计算X轴位置：进度比例 * 父容器宽度 - 自身宽度的一半
                // 这样中心点对齐进度点
                var x = (parentWidth * ratio) - (viewWidth / 2)
                
                // 边界限制
                if (x < 0) x = 0f
                if (x + viewWidth > parentWidth) x = (parentWidth - viewWidth).toFloat()
                
                view.x = x
                // Y轴位置：通常在进度条上方。这里假设是底部对齐，往上偏移一些
                val parentHeight = (view.parent as? View)?.height ?: 0
                view.y = (parentHeight - view.height - 100).toFloat() // 假设进度条高度+margin约100px
            }
        }
    }

    fun dismiss() {
        previewLayout?.visibility = View.INVISIBLE
        isShowing = false
    }

    fun release() {
        scope.cancel()
        bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmaps.clear()
        (previewLayout?.parent as? ViewGroup)?.removeView(previewLayout)
    }

    private fun stringForTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
